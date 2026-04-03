package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.han.ai.domain.dto.AiChatRequest;
import com.han.ai.domain.dto.AiMessageEditRequest;
import com.han.ai.domain.po.AiAgentPo;
import com.han.ai.domain.po.AiChatMessagePo;
import com.han.ai.domain.po.AiConversationPo;
import com.han.ai.domain.po.AiKnowledgeBasePo;
import com.han.ai.domain.po.AiMcpServerPo;
import com.han.ai.domain.po.AiModelPo;
import com.han.ai.domain.po.AiParagraphPo;
import com.han.ai.domain.po.AiWorkflowPo;
import com.han.ai.domain.query.AiConversationQuery;
import com.han.ai.domain.vo.AiChatKnowledgeSourceVo;
import com.han.ai.domain.vo.AiChatToolTraceVo;
import com.han.ai.mapper.AiAgentMapper;
import com.han.ai.mapper.AiChatMessageMapper;
import com.han.ai.mapper.AiConversationMapper;
import com.han.ai.mapper.AiKnowledgeBaseMapper;
import com.han.ai.mapper.AiMcpServerMapper;
import com.han.ai.mapper.AiModelMapper;
import com.han.ai.mapper.AiParagraphMapper;
import com.han.ai.mapper.AiWorkflowMapper;
import com.han.ai.service.IAiChatService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.security.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * AI chat service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl extends AiServiceSupport implements IAiChatService {

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final long SSE_TIMEOUT = 60_000L;
    private static final int HISTORY_MESSAGE_LIMIT = 12;

    private final AiConversationMapper aiConversationMapper;
    private final AiChatMessageMapper aiChatMessageMapper;
    private final AiKnowledgeBaseMapper aiKnowledgeBaseMapper;
    private final AiMcpServerMapper aiMcpServerMapper;
    private final AiModelMapper aiModelMapper;
    private final AiParagraphMapper aiParagraphMapper;
    private final AiAgentMapper aiAgentMapper;
    private final AiWorkflowMapper aiWorkflowMapper;
    private final AiModelCredentialResolver credentialResolver;
    private final AiOpenAiCompatibleClient openAiCompatibleClient;

    @Override
    public PageResult<AiConversationPo> selectConversationPage(AiConversationQuery query) {
        AiConversationQuery safeQuery = query != null ? query : new AiConversationQuery();
        int pageNum = normalizePageNum(safeQuery.getPageNum());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        LambdaQueryWrapper<AiConversationPo> wrapper = new LambdaQueryWrapper<AiConversationPo>()
                .eq(AiConversationPo::getUserId, requiredUserId())
                .orderByDesc(AiConversationPo::getUpdateTime)
                .orderByDesc(AiConversationPo::getConversationId);
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiConversationPo::getTenantId, tenantId);
        }
        if (safeQuery.getWorkflowId() != null) {
            wrapper.eq(AiConversationPo::getWorkflowId, safeQuery.getWorkflowId());
        }
        Page<AiConversationPo> page = aiConversationMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public List<AiChatMessagePo> selectMessages(Long conversationId) {
        AiConversationPo conversation = requireConversation(conversationId);
        List<AiChatMessagePo> messages = aiChatMessageMapper.selectList(new LambdaQueryWrapper<AiChatMessagePo>()
                .eq(AiChatMessagePo::getConversationId, conversationId)
                .orderByAsc(AiChatMessagePo::getSortOrder)
                .orderByAsc(AiChatMessagePo::getMessageId));
        return enrichConversationMessages(messages, buildContextFromConversation(conversation));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiChatMessagePo send(AiChatRequest request) {
        GeneratedReply reply = createGenericReply(request);
        return reply.assistantMessage();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SseEmitter stream(AiChatRequest request) {
        GeneratedReply reply = createGenericReply(request);
        return buildEmitter(reply.assistantMessage());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SseEmitter regenerate(Long conversationId) {
        GeneratedReply reply = regenerateReply(conversationId);
        return buildEmitter(reply.assistantMessage());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SseEmitter editRegenerate(AiMessageEditRequest request) {
        GeneratedReply reply = editAndRegenerate(request);
        return buildEmitter(reply.assistantMessage());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Long conversationId) {
        requireConversation(conversationId);
        aiChatMessageMapper.delete(new LambdaQueryWrapper<AiChatMessagePo>()
                .eq(AiChatMessagePo::getConversationId, conversationId));
        aiConversationMapper.deleteById(conversationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearConversation(Long conversationId) {
        AiConversationPo conversation = requireConversation(conversationId);
        aiChatMessageMapper.delete(new LambdaQueryWrapper<AiChatMessagePo>()
                .eq(AiChatMessagePo::getConversationId, conversationId));
        conversation.setMessageCount(0);
        conversation.setUpdateTime(now());
        aiConversationMapper.updateById(conversation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameConversation(Long conversationId, String title) {
        if (!StringUtils.hasText(title)) {
            throw new BusinessException("会话标题不能为空");
        }
        AiConversationPo conversation = requireConversation(conversationId);
        conversation.setTitle(truncateTitle(title.trim()));
        conversation.setUpdateTime(now());
        aiConversationMapper.updateById(conversation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String chatWithAgent(Long agentId, String message, Long conversationId) {
        AiAgentPo agent = requireAgent(agentId);
        if (!"1".equals(agent.getPublishedRaw())) {
            throw new BusinessException("智能体未发布，无法对话");
        }
        ChatContext context = ChatContext.agent(conversationId, agent.getModelId(), agent.getAgentName(),
                agent.getSystemPrompt(), parseIdList(agent.getKnowledgeBaseIds()), parseIdList(agent.getMcpServerIds()));
        return appendReply(context, message).assistantMessage().getContent();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String chatWithWorkflow(Long workflowId, String message, Long conversationId) {
        AiWorkflowPo workflow = requireWorkflow(workflowId);
        if (!"1".equals(workflow.getPublished())) {
            throw new BusinessException("工作流未发布，无法对话");
        }
        ChatContext context = ChatContext.workflow(conversationId, workflow.getWorkflowId(), workflow.getModelId(),
                workflow.getWorkflowName(), workflow.getSystemPrompt(), parseIdList(workflow.getKnowledgeBaseIds()),
                parseIdList(workflow.getMcpServerIds()));
        return appendReply(context, message).assistantMessage().getContent();
    }

    private GeneratedReply createGenericReply(AiChatRequest request) {
        if (request == null) {
            throw new BusinessException("对话请求不能为空");
        }
        ChatContext context;
        if (request.getWorkflowId() != null) {
            AiWorkflowPo workflow = aiWorkflowMapper.selectById(request.getWorkflowId());
            if (workflow == null) {
                throw new BusinessException("宸ヤ綔娴佷笉瀛樺湪");
            }
            context = ChatContext.workflow(request.getConversationId(), workflow.getWorkflowId(), workflow.getModelId(),
                    workflow.getWorkflowName(), workflow.getSystemPrompt(), parseIdList(workflow.getKnowledgeBaseIds()),
                    parseIdList(workflow.getMcpServerIds()));
        } else {
            context = ChatContext.general(request.getConversationId(), request.getModelId());
        }
        return appendReply(context, request.getMessage());
    }

    private GeneratedReply regenerateReply(Long conversationId) {
        AiConversationPo conversation = requireConversation(conversationId);
        List<AiChatMessagePo> messages = selectMessages(conversationId);
        if (messages.isEmpty()) {
            throw new BusinessException("当前会话暂无可重新生成的消息");
        }
        AiChatMessagePo lastMessage = messages.get(messages.size() - 1);
        if (ROLE_ASSISTANT.equals(lastMessage.getRole())) {
            aiChatMessageMapper.deleteById(lastMessage.getMessageId());
            messages.remove(messages.size() - 1);
        }
        AiChatMessagePo lastUserMessage = findLastUserMessage(messages);
        if (lastUserMessage == null) {
            throw new BusinessException("当前会话缺少用户消息，无法重新生成");
        }
        refreshConversationCount(conversation);
        return appendAssistantMessage(conversation, buildContextFromConversation(conversation), lastUserMessage.getContent());
    }

    private GeneratedReply editAndRegenerate(AiMessageEditRequest request) {
        if (request == null || request.getConversationId() == null || request.getMessageId() == null) {
            throw new BusinessException("编辑重新生成参数不完整");
        }
        AiConversationPo conversation = requireConversation(request.getConversationId());
        AiChatMessagePo targetMessage = aiChatMessageMapper.selectById(request.getMessageId());
        if (targetMessage == null || !conversation.getConversationId().equals(targetMessage.getConversationId())) {
            throw new BusinessException("原始消息不存在");
        }
        if (!ROLE_USER.equals(targetMessage.getRole())) {
            throw new BusinessException("仅支持编辑用户消息");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BusinessException("编辑后的消息内容不能为空");
        }
        aiChatMessageMapper.delete(new LambdaQueryWrapper<AiChatMessagePo>()
                .eq(AiChatMessagePo::getConversationId, conversation.getConversationId())
                .ge(AiChatMessagePo::getSortOrder, targetMessage.getSortOrder()));
        refreshConversationCount(conversation);

        AiChatMessagePo userMessage = appendUserMessage(conversation.getConversationId(), request.getContent().trim());
        refreshConversationCount(conversation);
        return appendAssistantMessage(conversation, buildContextFromConversation(conversation), userMessage.getContent());
    }

    private GeneratedReply appendReply(ChatContext context, String message) {
        String normalizedMessage = normalizeMessage(message);
        AiConversationPo conversation = prepareConversation(context, normalizedMessage);
        ChatContext effectiveContext = context.conversationId() != null ? buildContextFromConversation(conversation) : context;
        appendUserMessage(conversation.getConversationId(), normalizedMessage);
        refreshConversationCount(conversation);
        return appendAssistantMessage(conversation, effectiveContext, normalizedMessage);
    }

    private GeneratedReply appendAssistantMessage(AiConversationPo conversation, ChatContext context, String userMessage) {
        List<AiParagraphPo> hitParagraphs = searchKnowledgeParagraphs(context.knowledgeBaseIds(), userMessage);
        String assistantContent = buildAssistantReply(conversation, context, userMessage, hitParagraphs);
        AiChatMessagePo assistantMessage = new AiChatMessagePo();
        assistantMessage.setConversationId(conversation.getConversationId());
        assistantMessage.setRole(ROLE_ASSISTANT);
        assistantMessage.setContent(assistantContent);
        assistantMessage.setTokenCount(estimateTokenCount(assistantContent));
        assistantMessage.setSortOrder(nextSortOrder(conversation.getConversationId()));
        assistantMessage.setCreateTime(now());
        aiChatMessageMapper.insert(assistantMessage);

        refreshConversationCount(conversation);
        enrichAssistantMessage(assistantMessage, context, userMessage, hitParagraphs);
        return new GeneratedReply(conversation, assistantMessage);
    }

    private AiConversationPo prepareConversation(ChatContext context, String firstMessage) {
        if (context.conversationId() != null) {
            return requireConversation(context.conversationId());
        }
        AiConversationPo conversation = new AiConversationPo();
        conversation.setTitle(truncateTitle(context.titlePrefix() + " - " + firstMessage));
        conversation.setWorkflowId(context.workflowId());
        conversation.setModelId(context.modelId());
        conversation.setUserId(requiredUserId());
        conversation.setMessageCount(0);
        conversation.setTenantId(resolveTenantIdForWrite());
        conversation.setCreateTime(now());
        conversation.setUpdateTime(now());
        aiConversationMapper.insert(conversation);
        return conversation;
    }

    private AiChatMessagePo appendUserMessage(Long conversationId, String message) {
        AiChatMessagePo userMessage = new AiChatMessagePo();
        userMessage.setConversationId(conversationId);
        userMessage.setRole(ROLE_USER);
        userMessage.setContent(message);
        userMessage.setTokenCount(estimateTokenCount(message));
        userMessage.setSortOrder(nextSortOrder(conversationId));
        userMessage.setCreateTime(now());
        aiChatMessageMapper.insert(userMessage);
        return userMessage;
    }

    private String buildAssistantReply(AiConversationPo conversation, ChatContext context, String userMessage,
                                       List<AiParagraphPo> hitParagraphs) {
        AiModelPo model = resolveModel(context.modelId());
        String modelReply = tryBuildModelReply(conversation, context, model, hitParagraphs);
        if (StringUtils.hasText(modelReply)) {
            return modelReply;
        }
        return buildFallbackAssistantReply(context, model, userMessage, hitParagraphs);
    }

    private String tryBuildModelReply(AiConversationPo conversation, ChatContext context, AiModelPo model,
                                      List<AiParagraphPo> hitParagraphs) {
        if (model == null) {
            return null;
        }
        String apiKey = credentialResolver.resolveApiKey(model);
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        List<AiOpenAiCompatibleClient.ProviderMessage> messages = buildProviderMessages(conversation, context, hitParagraphs);
        try {
            return openAiCompatibleClient.chatCompletion(model, apiKey, messages, model.getMaxTokens());
        } catch (BusinessException ex) {
            log.warn("AI provider fallback triggered, provider={}, modelCode={}, conversationId={}, reason={}",
                    model.getProvider(), model.getModelCode(), conversation.getConversationId(), ex.getMessage());
            return null;
        }
    }

    private List<AiOpenAiCompatibleClient.ProviderMessage> buildProviderMessages(AiConversationPo conversation,
                                                                                 ChatContext context,
                                                                                 List<AiParagraphPo> hitParagraphs) {
        List<AiOpenAiCompatibleClient.ProviderMessage> messages = new ArrayList<>();
        String systemPrompt = buildSystemPrompt(context, hitParagraphs);
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(AiOpenAiCompatibleClient.ProviderMessage.system(systemPrompt));
        }

        List<AiChatMessagePo> history = selectMessages(conversation.getConversationId());
        int startIndex = Math.max(0, history.size() - HISTORY_MESSAGE_LIMIT);
        for (int index = startIndex; index < history.size(); index++) {
            AiChatMessagePo historyMessage = history.get(index);
            if (!StringUtils.hasText(historyMessage.getContent())) {
                continue;
            }
            if (ROLE_USER.equals(historyMessage.getRole())) {
                messages.add(AiOpenAiCompatibleClient.ProviderMessage.user(historyMessage.getContent()));
            } else if (ROLE_ASSISTANT.equals(historyMessage.getRole())) {
                messages.add(AiOpenAiCompatibleClient.ProviderMessage.assistant(historyMessage.getContent()));
            }
        }
        if (messages.isEmpty()) {
            messages.add(AiOpenAiCompatibleClient.ProviderMessage.user("你好"));
        }
        return messages;
    }

    private String buildSystemPrompt(ChatContext context, List<AiParagraphPo> hitParagraphs) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(context.systemPrompt())) {
            builder.append(context.systemPrompt().trim()).append("\n\n");
        }
        if (StringUtils.hasText(context.sourceName())) {
            builder.append("当前对话来源：").append(context.sourceName().trim()).append("。\n");
        }
        if (!hitParagraphs.isEmpty()) {
            builder.append("以下是命中的知识库上下文，请优先引用并结合它们回答：\n");
            int index = 1;
            for (AiParagraphPo paragraph : hitParagraphs) {
                builder.append(index++)
                        .append(". ")
                        .append(excerpt(paragraph.getContent(), 200))
                        .append('\n');
            }
        }
        return builder.toString().trim();
    }

    private String buildFallbackAssistantReply(ChatContext context, AiModelPo model, String userMessage,
                                               List<AiParagraphPo> hitParagraphs) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(context.sourceName())) {
            builder.append("已按“").append(context.sourceName()).append("”的上下文处理你的问题。\n");
        } else {
            builder.append("已收到你的问题，并基于当前 AI 配置生成兜底回复。\n");
        }
        if (model != null) {
            builder.append("当前模型：").append(model.getModelName()).append(" (").append(model.getModelCode()).append(")\n");
        }
        if (StringUtils.hasText(context.systemPrompt())) {
            builder.append("角色设定已生效，回答会遵循预设的系统提示词。\n");
        }
        builder.append("问题：").append(userMessage).append('\n');
        if (!hitParagraphs.isEmpty()) {
            builder.append("知识库命中：\n");
            int index = 1;
            for (AiParagraphPo paragraph : hitParagraphs) {
                builder.append(index++)
                        .append(". ")
                        .append(excerpt(paragraph.getContent(), 120))
                        .append('\n');
            }
            builder.append("建议：优先结合上面的知识命中内容继续细化答案。若需要正式生成式输出，请配置可用的模型 API Key。");
        } else {
            builder.append("当前未命中可直接引用的知识片段，将按已有模型和提示词给出基础建议。\n");
            builder.append("建议：可以继续补充更具体的上下文、目标和约束，我会基于当前配置继续整理。");
        }
        return builder.toString().trim();
    }

    private AiModelPo resolveModel(Long modelId) {
        if (modelId != null) {
            return aiModelMapper.selectById(modelId);
        }
        return aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelPo>()
                .eq(AiModelPo::getStatus, STATUS_ENABLED)
                .eq(AiModelPo::getModelType, "LLM")
                .orderByAsc(AiModelPo::getModelId)
                .last("LIMIT 1"));
    }

    private List<AiParagraphPo> searchKnowledgeParagraphs(List<Long> knowledgeBaseIds, String userMessage) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || !StringUtils.hasText(userMessage)) {
            return List.of();
        }
        Set<String> searchTerms = new LinkedHashSet<>();
        String normalized = userMessage.trim();
        searchTerms.add(normalized);
        for (String term : normalized.split("[\\s,，。；;、]+")) {
            if (term.length() >= 2) {
                searchTerms.add(term);
            }
        }
        if (normalized.length() > 12) {
            searchTerms.add(normalized.substring(0, 12));
        }

        LambdaQueryWrapper<AiParagraphPo> wrapper = new LambdaQueryWrapper<AiParagraphPo>()
                .in(AiParagraphPo::getKbId, knowledgeBaseIds)
                .eq(AiParagraphPo::getStatus, STATUS_ENABLED)
                .eq(AiParagraphPo::getDelFlag, 0)
                .and(q -> {
                    boolean first = true;
                    for (String term : searchTerms) {
                        if (!StringUtils.hasText(term)) {
                            continue;
                        }
                        if (first) {
                            q.like(AiParagraphPo::getContent, term);
                            first = false;
                        } else {
                            q.or().like(AiParagraphPo::getContent, term);
                        }
                    }
                })
                .orderByDesc(AiParagraphPo::getHitCount)
                .orderByDesc(AiParagraphPo::getCreateTime)
                .last("LIMIT 5");
        return aiParagraphMapper.selectList(wrapper);
    }

    private List<AiChatMessagePo> enrichConversationMessages(List<AiChatMessagePo> messages, ChatContext context) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        String latestUserPrompt = null;
        for (AiChatMessagePo message : messages) {
            if (ROLE_USER.equals(message.getRole())) {
                latestUserPrompt = message.getContent();
                continue;
            }
            if (ROLE_ASSISTANT.equals(message.getRole())) {
                enrichAssistantMessage(message, context, latestUserPrompt);
            }
        }
        return messages;
    }

    private void enrichAssistantMessage(AiChatMessagePo assistantMessage, ChatContext context, String userPrompt) {
        enrichAssistantMessage(assistantMessage, context, userPrompt, null);
    }

    private void enrichAssistantMessage(AiChatMessagePo assistantMessage, ChatContext context, String userPrompt,
                                        List<AiParagraphPo> hitParagraphs) {
        if (assistantMessage == null || !ROLE_ASSISTANT.equals(assistantMessage.getRole())) {
            return;
        }
        assistantMessage.setKnowledgeSources(buildKnowledgeSources(context.knowledgeBaseIds(), userPrompt, hitParagraphs));
        assistantMessage.setToolExecutions(buildToolTraceSummaries(context.mcpServerIds()));
    }

    private List<AiChatKnowledgeSourceVo> buildKnowledgeSources(List<Long> knowledgeBaseIds, String userPrompt) {
        return buildKnowledgeSources(knowledgeBaseIds, userPrompt, null);
    }

    private List<AiChatKnowledgeSourceVo> buildKnowledgeSources(List<Long> knowledgeBaseIds, String userPrompt,
                                                                List<AiParagraphPo> precomputedHitParagraphs) {
        List<AiParagraphPo> hitParagraphs = precomputedHitParagraphs != null
                ? precomputedHitParagraphs
                : searchKnowledgeParagraphs(knowledgeBaseIds, userPrompt);
        if (hitParagraphs.isEmpty()) {
            return List.of();
        }
        Map<Long, AiKnowledgeBasePo> knowledgeBaseMap = loadKnowledgeBaseMap(knowledgeBaseIds);
        List<AiChatKnowledgeSourceVo> results = new ArrayList<>();
        for (AiParagraphPo paragraph : hitParagraphs) {
            AiKnowledgeBasePo knowledgeBase = knowledgeBaseMap.get(paragraph.getKbId());
            AiChatKnowledgeSourceVo source = new AiChatKnowledgeSourceVo();
            source.setKbId(paragraph.getKbId());
            source.setKbName(knowledgeBase != null ? knowledgeBase.getKbName() : null);
            source.setKbType(knowledgeBase != null ? knowledgeBase.getKbType() : null);
            source.setKbStatus(knowledgeBase != null ? knowledgeBase.getStatus() : null);
            source.setDocumentCount(knowledgeBase != null ? knowledgeBase.getDocumentCount() : null);
            source.setParagraphCount(knowledgeBase != null ? knowledgeBase.getParagraphCount() : null);
            source.setCharCount(knowledgeBase != null ? knowledgeBase.getCharCount() : null);
            source.setParagraphId(paragraph.getParagraphId());
            source.setParagraphTitle(paragraph.getTitle());
            source.setHitCount(paragraph.getHitCount());
            source.setExcerpt(excerpt(paragraph.getContent(), 160));
            results.add(source);
        }
        return results;
    }

    private Map<Long, AiKnowledgeBasePo> loadKnowledgeBaseMap(List<Long> knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AiKnowledgeBasePo> knowledgeBaseMap = new HashMap<>();
        for (AiKnowledgeBasePo knowledgeBase : aiKnowledgeBaseMapper.selectBatchIds(knowledgeBaseIds)) {
            if (knowledgeBase != null && knowledgeBase.getKbId() != null) {
                knowledgeBaseMap.put(knowledgeBase.getKbId(), knowledgeBase);
            }
        }
        return knowledgeBaseMap;
    }

    private List<AiChatToolTraceVo> buildToolTraceSummaries(List<Long> mcpServerIds) {
        if (mcpServerIds == null || mcpServerIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<AiMcpServerPo> wrapper = new LambdaQueryWrapper<AiMcpServerPo>()
                .in(AiMcpServerPo::getMcpId, mcpServerIds);
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiMcpServerPo::getTenantId, tenantId);
        }
        List<AiMcpServerPo> servers = aiMcpServerMapper.selectList(wrapper);
        if (servers.isEmpty()) {
            return List.of();
        }
        Map<Long, AiMcpServerPo> serverMap = new HashMap<>();
        for (AiMcpServerPo server : servers) {
            if (server.getMcpId() != null) {
                serverMap.put(server.getMcpId(), server);
            }
        }
        List<AiChatToolTraceVo> results = new ArrayList<>();
        for (Long mcpServerId : mcpServerIds) {
            AiMcpServerPo server = serverMap.get(mcpServerId);
            if (server == null) {
                continue;
            }
            List<String> toolNames = parseToolNames(server.getTools());
            AiChatToolTraceVo trace = new AiChatToolTraceVo();
            trace.setMcpId(server.getMcpId());
            trace.setServerName(server.getServerName());
            trace.setTransportType(server.getTransportType());
            trace.setStatus(server.getStatus());
            trace.setToolCount(toolNames.size());
            trace.setToolNames(toolNames);
            trace.setSummary(buildToolTraceSummary(server, toolNames));
            results.add(trace);
        }
        return results;
    }

    private List<String> parseToolNames(String toolsJson) {
        if (!StringUtils.hasText(toolsJson)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> toolList = XuJsonUtil.parseObject(toolsJson,
                    new TypeReference<List<Map<String, Object>>>() {});
            if (toolList == null || toolList.isEmpty()) {
                return List.of();
            }
            List<String> toolNames = new ArrayList<>();
            for (Map<String, Object> tool : toolList) {
                Object name = tool != null ? tool.get("name") : null;
                if (name != null && StringUtils.hasText(String.valueOf(name))) {
                    toolNames.add(String.valueOf(name));
                }
            }
            return toolNames;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private String buildToolTraceSummary(AiMcpServerPo server, List<String> toolNames) {
        StringBuilder builder = new StringBuilder();
        builder.append("已挂载 ")
                .append(server.getServerName())
                .append("，传输方式 ")
                .append(server.getTransportType());
        if (!toolNames.isEmpty()) {
            builder.append("，可用工具 ").append(String.join("、", toolNames));
        } else {
            builder.append("，当前还没有工具元数据");
        }
        return builder.toString();
    }

    private ChatContext buildContextFromConversation(AiConversationPo conversation) {
        if (conversation.getWorkflowId() != null) {
            AiWorkflowPo workflow = aiWorkflowMapper.selectById(conversation.getWorkflowId());
            if (workflow != null) {
                return ChatContext.workflow(conversation.getConversationId(), workflow.getWorkflowId(), workflow.getModelId(),
                        workflow.getWorkflowName(), workflow.getSystemPrompt(), parseIdList(workflow.getKnowledgeBaseIds()),
                        parseIdList(workflow.getMcpServerIds()));
            }
        }
        return ChatContext.general(conversation.getConversationId(), conversation.getModelId());
    }

    private List<Long> parseIdList(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return List.of();
        }
        try {
            List<Long> parsed = XuJsonUtil.parseObject(rawJson, new TypeReference<List<Long>>() {});
            return parsed != null ? parsed : List.of();
        } catch (RuntimeException ignored) {
            String sanitized = rawJson.replace("[", "").replace("]", "").replace("\"", "");
            List<Long> result = new ArrayList<>();
            for (String segment : sanitized.split(",")) {
                String trimmed = segment.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        result.add(Long.valueOf(trimmed));
                    } catch (NumberFormatException ignoredNumber) {
                        // Ignore illegal ids and keep remaining values.
                    }
                }
            }
            return result;
        }
    }

    private SseEmitter buildEmitter(AiChatMessagePo assistantMessage) {
        String content = assistantMessage != null ? assistantMessage.getContent() : "";
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        CompletableFuture.runAsync(() -> {
            try {
                for (String chunk : splitChunks(content)) {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(XuJsonUtil.toJsonString(Map.of("type", "delta", "content", chunk)), MediaType.APPLICATION_JSON));
                    Thread.sleep(25L);
                }
                Map<String, Object> metaPayload = buildStreamMeta(assistantMessage);
                if (!metaPayload.isEmpty()) {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(XuJsonUtil.toJsonString(Map.of("type", "meta", "content", metaPayload)),
                                    MediaType.APPLICATION_JSON));
                }
                emitter.send(SseEmitter.event().name("message").data("[DONE]"));
                emitter.complete();
            } catch (IOException | InterruptedException ex) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(XuJsonUtil.toJsonString(Map.of("type", "error",
                                    "content", ex.getMessage() == null ? "AI响应异常" : ex.getMessage())), MediaType.APPLICATION_JSON));
                } catch (IOException ignored) {
                    // Ignore secondary SSE send failures.
                }
                emitter.complete();
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        return emitter;
    }

    private Map<String, Object> buildStreamMeta(AiChatMessagePo assistantMessage) {
        if (assistantMessage == null) {
            return Map.of();
        }
        Map<String, Object> meta = new HashMap<>();
        if (assistantMessage.getMessageId() != null) {
            meta.put("messageId", assistantMessage.getMessageId());
        }
        if (assistantMessage.getTokenCount() != null) {
            meta.put("tokenCount", assistantMessage.getTokenCount());
        }
        if (assistantMessage.getKnowledgeSources() != null && !assistantMessage.getKnowledgeSources().isEmpty()) {
            meta.put("knowledgeSources", assistantMessage.getKnowledgeSources());
        }
        if (assistantMessage.getToolExecutions() != null && !assistantMessage.getToolExecutions().isEmpty()) {
            meta.put("toolExecutions", assistantMessage.getToolExecutions());
        }
        return meta;
    }

    private List<String> splitChunks(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of("");
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + 48, content.length());
            chunks.add(content.substring(start, end));
            start = end;
        }
        return chunks;
    }

    private String excerpt(String content, int maxLength) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String truncateTitle(String title) {
        String normalized = title == null ? "新对话" : title.trim();
        if (normalized.isEmpty()) {
            normalized = "新对话";
        }
        return normalized.length() > 40 ? normalized.substring(0, 40) : normalized;
    }

    private String normalizeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            throw new BusinessException("消息内容不能为空");
        }
        return message.trim();
    }

    private int estimateTokenCount(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 4.0D));
    }

    private int nextSortOrder(Long conversationId) {
        Long count = aiChatMessageMapper.selectCount(new LambdaQueryWrapper<AiChatMessagePo>()
                .eq(AiChatMessagePo::getConversationId, conversationId));
        return count == null ? 1 : count.intValue() + 1;
    }

    private void refreshConversationCount(AiConversationPo conversation) {
        Long count = aiChatMessageMapper.selectCount(new LambdaQueryWrapper<AiChatMessagePo>()
                .eq(AiChatMessagePo::getConversationId, conversation.getConversationId()));
        conversation.setMessageCount(count == null ? 0 : count.intValue());
        conversation.setUpdateTime(LocalDateTime.now());
        aiConversationMapper.updateById(conversation);
    }

    private AiConversationPo requireConversation(Long conversationId) {
        if (conversationId == null) {
            throw new BusinessException("会话ID不能为空");
        }
        AiConversationPo conversation = aiConversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException("会话不存在");
        }
        if (!requiredUserId().equals(conversation.getUserId())) {
            throw new BusinessException("无权访问该会话");
        }
        Long tenantId = currentTenantId();
        if (tenantId != null && !tenantId.equals(conversation.getTenantId())) {
            throw new BusinessException("无权访问该会话");
        }
        return conversation;
    }

    private AiAgentPo requireAgent(Long agentId) {
        if (agentId == null) {
            throw new BusinessException("智能体ID不能为空");
        }
        AiAgentPo agent = aiAgentMapper.selectById(agentId);
        if (agent == null || (agent.getDeleted() != null && agent.getDeleted() != 0)) {
            throw new BusinessException("智能体不存在");
        }
        return agent;
    }

    private AiWorkflowPo requireWorkflow(Long workflowId) {
        if (workflowId == null) {
            throw new BusinessException("工作流ID不能为空");
        }
        AiWorkflowPo workflow = aiWorkflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new BusinessException("工作流不存在");
        }
        return workflow;
    }

    private AiChatMessagePo findLastUserMessage(List<AiChatMessagePo> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            AiChatMessagePo message = messages.get(index);
            if (ROLE_USER.equals(message.getRole())) {
                return message;
            }
        }
        return null;
    }

    private Long requiredUserId() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("未获取到当前登录用户");
        }
        return userId;
    }

    private record GeneratedReply(AiConversationPo conversation, AiChatMessagePo assistantMessage) {
    }

    private record ChatContext(Long conversationId, Long workflowId, Long modelId, String titlePrefix,
                               String sourceName, String systemPrompt, List<Long> knowledgeBaseIds,
                               List<Long> mcpServerIds) {

        private static ChatContext general(Long conversationId, Long modelId) {
            return new ChatContext(conversationId, null, modelId, "通用对话", null, null, List.of(), List.of());
        }

        private static ChatContext agent(Long conversationId, Long modelId, String agentName,
                                         String systemPrompt, List<Long> knowledgeBaseIds, List<Long> mcpServerIds) {
            return new ChatContext(conversationId, null, modelId, "智能体对话", agentName, systemPrompt,
                    knowledgeBaseIds, mcpServerIds);
        }

        private static ChatContext workflow(Long conversationId, Long workflowId, Long modelId, String workflowName,
                                            String systemPrompt, List<Long> knowledgeBaseIds, List<Long> mcpServerIds) {
            return new ChatContext(conversationId, workflowId, modelId, "工作流对话", workflowName, systemPrompt,
                    knowledgeBaseIds, mcpServerIds);
        }
    }
}
