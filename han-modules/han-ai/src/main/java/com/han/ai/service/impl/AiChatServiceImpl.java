package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.han.ai.domain.dto.AiChatImageRequest;
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
import com.han.ai.domain.vo.AiChatImageVo;
import com.han.ai.domain.vo.AiChatKnowledgeSourceVo;
import com.han.ai.domain.vo.AiChatToolTraceVo;
import com.han.ai.domain.vo.AiFlowNodeTraceVo;
import com.han.ai.mapper.AiAgentMapper;
import com.han.ai.mapper.AiChatMessageMapper;
import com.han.ai.mapper.AiConversationMapper;
import com.han.ai.mapper.AiKnowledgeBaseMapper;
import com.han.ai.mapper.AiMcpServerMapper;
import com.han.ai.mapper.AiModelMapper;
import com.han.ai.mapper.AiWorkflowMapper;
import com.han.ai.service.IAiChatService;
import com.han.ai.service.IAiKnowledgeRetrievalService;
import com.han.ai.service.IAiKnowledgeRetrievalService.ScoredParagraph;
import com.han.api.file.FileServiceClient;
import com.han.api.file.domain.FileBase64DTO;
import com.han.api.file.domain.FileDTO;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.security.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final int MAX_CHAT_IMAGES = 4;
    private static final int MAX_TOOL_CALL_ROUNDS = 5;

    private final AiConversationMapper aiConversationMapper;
    private final AiChatMessageMapper aiChatMessageMapper;
    private final AiKnowledgeBaseMapper aiKnowledgeBaseMapper;
    private final AiMcpServerMapper aiMcpServerMapper;
    private final AiModelMapper aiModelMapper;
    private final AiAgentMapper aiAgentMapper;
    private final AiWorkflowMapper aiWorkflowMapper;
    private final AiModelCredentialResolver credentialResolver;
    private final AiOpenAiCompatibleClient openAiCompatibleClient;
    private final IAiKnowledgeRetrievalService knowledgeRetrievalService;
    private final FileServiceClient fileServiceClient;
    private final AiFlowEngine aiFlowEngine;
    private final AiMcpClientService aiMcpClientService;

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
    public String shareChat(AiAgentPo agent, String message, List<Map<String, String>> history) {
        String normalizedMessage = normalizeMessage(message);
        AiModelPo model = resolveModel(agent.getModelId());
        if (model == null) {
            throw new BusinessException("应用未配置可用模型");
        }
        String apiKey = credentialResolver.resolveApiKey(model);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("应用模型凭据未配置，请联系管理员");
        }
        List<Long> knowledgeBaseIds = parseIdList(agent.getKnowledgeBaseIds());
        List<ScoredParagraph> hitParagraphs = searchKnowledgeParagraphs(knowledgeBaseIds, normalizedMessage);
        ChatContext context = ChatContext.agent(null, agent.getModelId(), agent.getAgentName(),
                agent.getSystemPrompt(), knowledgeBaseIds, List.of());

        List<AiOpenAiCompatibleClient.ProviderMessage> messages = new ArrayList<>();
        String systemPrompt = buildSystemPrompt(context, hitParagraphs);
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(AiOpenAiCompatibleClient.ProviderMessage.system(systemPrompt));
        }
        // 无状态：历史由前端携带（最多 10 轮），仅接受 user/assistant 角色
        if (history != null) {
            int startIndex = Math.max(0, history.size() - 20);
            for (int index = startIndex; index < history.size(); index++) {
                Map<String, String> item = history.get(index);
                if (item == null) {
                    continue;
                }
                String role = item.get("role");
                String content = item.get("content");
                if (!StringUtils.hasText(content)) {
                    continue;
                }
                if (ROLE_USER.equals(role)) {
                    messages.add(AiOpenAiCompatibleClient.ProviderMessage.user(content));
                } else if (ROLE_ASSISTANT.equals(role)) {
                    messages.add(AiOpenAiCompatibleClient.ProviderMessage.assistant(content));
                }
            }
        }
        messages.add(AiOpenAiCompatibleClient.ProviderMessage.user(normalizedMessage));
        return openAiCompatibleClient.chatCompletion(model, apiKey, messages, agent.getMaxTokens());
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiChatMessagePo generateImage(AiChatImageRequest request) {
        if (request == null || !StringUtils.hasText(request.getPrompt())) {
            throw new BusinessException("图片生成提示词不能为空");
        }
        String prompt = request.getPrompt().trim();
        AiModelPo model = resolveImageModel(request.getModelId());
        String apiKey = credentialResolver.resolveApiKey(model);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("图片模型 API Key 未配置，请先在模型管理配置凭据");
        }

        AiOpenAiCompatibleClient.ImageGenerationResult result = openAiCompatibleClient.imageGeneration(
                model, apiKey, prompt, List.of(), 1,
                StringUtils.hasText(request.getSize()) ? request.getSize().trim() : "1024x1024", "b64_json");
        List<AiChatImageVo> images = persistGeneratedImages(result.images());
        if (images.isEmpty()) {
            throw new BusinessException("图片生成成功但转存失败，请重试");
        }

        ChatContext context = ChatContext.general(request.getConversationId(), model.getModelId());
        AiConversationPo conversation = prepareConversation(context, "[图片生成] " + prompt);
        appendUserMessage(conversation.getConversationId(), prompt, null);

        AiChatMessagePo assistantMessage = new AiChatMessagePo();
        assistantMessage.setConversationId(conversation.getConversationId());
        assistantMessage.setRole(ROLE_ASSISTANT);
        assistantMessage.setContent("已按提示词生成图片（模型：" + model.getModelName() + "）。");
        assistantMessage.setTokenCount(0);
        assistantMessage.setSortOrder(nextSortOrder(conversation.getConversationId()));
        assistantMessage.setImages(XuJsonUtil.toJsonString(images));
        assistantMessage.setImageList(images);
        assistantMessage.setCreateTime(now());
        aiChatMessageMapper.insert(assistantMessage);
        refreshConversationCount(conversation);
        return assistantMessage;
    }

    private AiModelPo resolveImageModel(Long modelId) {
        AiModelPo model;
        if (modelId != null) {
            model = aiModelMapper.selectById(modelId);
            if (model == null) {
                throw new BusinessException("图片模型不存在");
            }
        } else {
            model = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelPo>()
                    .eq(AiModelPo::getStatus, STATUS_ENABLED)
                    .eq(AiModelPo::getModelType, "IMAGE")
                    .orderByAsc(AiModelPo::getModelId)
                    .last("LIMIT 1"));
            if (model == null) {
                throw new BusinessException("未配置可用的图片生成模型，请先到模型管理配置");
            }
        }
        if (!"IMAGE".equalsIgnoreCase(model.getModelType())) {
            throw new BusinessException("所选模型不是图片生成模型");
        }
        if (!STATUS_ENABLED.equals(model.getStatus())) {
            throw new BusinessException("图片模型未启用");
        }
        return model;
    }

    /**
     * 生成图转存文件服务（供应商 URL 通常短期有效，转存后拿稳定的公开代理地址）。
     */
    private List<AiChatImageVo> persistGeneratedImages(List<AiOpenAiCompatibleClient.GeneratedImage> generated) {
        List<AiChatImageVo> images = new ArrayList<>();
        if (generated == null) {
            return images;
        }
        for (AiOpenAiCompatibleClient.GeneratedImage image : generated) {
            byte[] bytes = null;
            if (StringUtils.hasText(image.base64Data())) {
                try {
                    bytes = Base64.getDecoder().decode(image.base64Data());
                } catch (IllegalArgumentException ex) {
                    log.warn("Generated image base64 decode failed, index={}", image.index(), ex);
                }
            }
            if (bytes == null && StringUtils.hasText(image.url())) {
                bytes = downloadImageBytes(image.url());
            }
            if (bytes == null || bytes.length == 0) {
                continue;
            }
            String extension = resolveImageExtension(image.mimeType());
            String fileName = "ai-gen-" + System.currentTimeMillis() + "-" + image.index() + "." + extension;
            byte[] uploadBytes = bytes;
            ByteArrayResource resource = new ByteArrayResource(uploadBytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            try {
                R<FileDTO> uploaded = fileServiceClient.upload(resource);
                if (uploaded != null && uploaded.getData() != null && StringUtils.hasText(uploaded.getData().getUrl())) {
                    images.add(new AiChatImageVo(uploaded.getData().getId(), uploaded.getData().getUrl(), fileName));
                } else {
                    log.warn("Generated image upload returned empty payload, fileName={}", fileName);
                }
            } catch (RuntimeException ex) {
                log.warn("Generated image upload failed, fileName={}", fileName, ex);
            }
        }
        return images;
    }

    private byte[] downloadImageBytes(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(60_000);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                log.warn("Generated image download failed, status={}, url={}", status, url);
                return null;
            }
            try (InputStream stream = connection.getInputStream()) {
                return stream.readAllBytes();
            }
        } catch (IOException ex) {
            log.warn("Generated image download IO error, url={}", url, ex);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String resolveImageExtension(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return "png";
        }
        return switch (mimeType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    private GeneratedReply createGenericReply(AiChatRequest request) {
        if (request == null) {
            throw new BusinessException("对话请求不能为空");
        }
        ChatContext context;
        if (request.getWorkflowId() != null) {
            AiWorkflowPo workflow = aiWorkflowMapper.selectById(request.getWorkflowId());
            if (workflow == null) {
                throw new BusinessException("工作流不存在");
            }
            context = ChatContext.workflow(request.getConversationId(), workflow.getWorkflowId(), workflow.getModelId(),
                    workflow.getWorkflowName(), workflow.getSystemPrompt(), parseIdList(workflow.getKnowledgeBaseIds()),
                    parseIdList(workflow.getMcpServerIds()));
        } else {
            context = ChatContext.general(request.getConversationId(), request.getModelId());
        }
        LoadedImages images = null;
        if (request.getImageFileIds() != null && !request.getImageFileIds().isEmpty()) {
            requireVisionSupport(context.modelId());
            images = loadImages(request.getImageFileIds());
        }
        return appendReply(context, request.getMessage(), images);
    }

    /**
     * 校验目标模型具备视觉输入能力（多模态图片理解）。
     */
    private void requireVisionSupport(Long modelId) {
        AiModelPo model = resolveModel(modelId);
        if (model == null) {
            throw new BusinessException("未找到可用模型，无法处理图片输入");
        }
        if (!"1".equals(model.getSupportsVision())) {
            throw new BusinessException("当前模型不支持图片理解，请切换支持视觉的模型");
        }
    }

    /**
     * 按文件ID经文件服务读取图片，产出附件元数据与 data URL（base64 注入模型，兼容内网部署）。
     */
    private LoadedImages loadImages(List<Long> imageFileIds) {
        if (imageFileIds.size() > MAX_CHAT_IMAGES) {
            throw new BusinessException("单次最多发送 " + MAX_CHAT_IMAGES + " 张图片");
        }
        Long tenantId = currentTenantId();
        List<AiChatImageVo> items = new ArrayList<>();
        List<String> dataUrls = new ArrayList<>();
        for (Long fileId : imageFileIds) {
            if (fileId == null) {
                continue;
            }
            R<FileBase64DTO> result;
            try {
                result = fileServiceClient.loadBase64(fileId);
            } catch (RuntimeException ex) {
                log.warn("Load chat image failed, fileId={}", fileId, ex);
                throw new BusinessException("图片读取失败，请重新上传");
            }
            if (result == null || result.getData() == null || !StringUtils.hasText(result.getData().getBase64())) {
                throw new BusinessException("图片不存在或已被清理，请重新上传");
            }
            FileBase64DTO file = result.getData();
            requireImageTenantAccess(tenantId, file, fileId);
            String mimeType = StringUtils.hasText(file.getMimeType()) ? file.getMimeType() : "image/png";
            items.add(new AiChatImageVo(file.getId(), file.getUrl(), file.getName()));
            dataUrls.add("data:" + mimeType + ";base64," + file.getBase64());
        }
        if (items.isEmpty()) {
            throw new BusinessException("图片附件无效，请重新上传");
        }
        return new LoadedImages(items, dataUrls);
    }

    /**
     * 图片附件租户归属校验：非管理员只能引用本租户或平台级（tenantId=0）文件，防止伪造 fileId 跨租户读图。
     */
    private void requireImageTenantAccess(Long tenantId, FileBase64DTO file, Long fileId) {
        if (tenantId == null) {
            return;
        }
        Long fileTenantId = file.getTenantId();
        if (fileTenantId != null && fileTenantId > 0 && !fileTenantId.equals(tenantId)) {
            log.warn("Cross-tenant chat image rejected, fileId={}, fileTenantId={}, currentTenantId={}",
                    fileId, fileTenantId, tenantId);
            throw new BusinessException("无权访问该图片附件");
        }
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
        return appendReply(context, message, null);
    }

    private GeneratedReply appendReply(ChatContext context, String message, LoadedImages images) {
        String normalizedMessage = normalizeMessage(message);
        AiConversationPo conversation = prepareConversation(context, normalizedMessage);
        ChatContext effectiveContext = context.conversationId() != null ? buildContextFromConversation(conversation) : context;
        appendUserMessage(conversation.getConversationId(), normalizedMessage, images != null ? images.items() : null);
        refreshConversationCount(conversation);
        return appendAssistantMessage(conversation, effectiveContext, normalizedMessage,
                images != null ? images.dataUrls() : null);
    }

    private GeneratedReply appendAssistantMessage(AiConversationPo conversation, ChatContext context, String userMessage) {
        return appendAssistantMessage(conversation, context, userMessage, (List<String>) null);
    }

    private GeneratedReply appendAssistantMessage(AiConversationPo conversation, ChatContext context, String userMessage,
                                                  List<String> pendingImageDataUrls) {
        AiWorkflowPo advancedWorkflow = resolveAdvancedWorkflow(context.workflowId());
        if (advancedWorkflow != null) {
            return appendFlowAssistantMessage(conversation, advancedWorkflow, userMessage);
        }
        List<ScoredParagraph> hitParagraphs = searchKnowledgeParagraphs(context.knowledgeBaseIds(), userMessage);
        ModelReply reply = buildAssistantReply(conversation, context, userMessage, hitParagraphs, pendingImageDataUrls);
        AiChatMessagePo assistantMessage = new AiChatMessagePo();
        assistantMessage.setConversationId(conversation.getConversationId());
        assistantMessage.setRole(ROLE_ASSISTANT);
        assistantMessage.setContent(reply.content());
        assistantMessage.setTokenCount(estimateTokenCount(reply.content()));
        assistantMessage.setSortOrder(nextSortOrder(conversation.getConversationId()));
        if (!reply.toolTraces().isEmpty()) {
            // 真实工具调用轨迹随消息落库（回显）并直接作为本次 toolExecutions
            assistantMessage.setMeta(XuJsonUtil.toJsonString(Map.of("toolCalls", reply.toolTraces())));
            assistantMessage.setToolExecutions(reply.toolTraces());
        }
        assistantMessage.setCreateTime(now());
        aiChatMessageMapper.insert(assistantMessage);

        refreshConversationCount(conversation);
        enrichAssistantMessage(assistantMessage, context, userMessage, hitParagraphs);
        return new GeneratedReply(conversation, assistantMessage);
    }

    /**
     * advanced 编排工作流判定：会话绑定的工作流为 advanced 且画布已有内容时返回，否则 null（走普通对话）。
     */
    private AiWorkflowPo resolveAdvancedWorkflow(Long workflowId) {
        if (workflowId == null) {
            return null;
        }
        AiWorkflowPo workflow = aiWorkflowMapper.selectById(workflowId);
        if (workflow == null || !"advanced".equals(workflow.getWorkflowType())) {
            return null;
        }
        String flowConfig = workflow.getFlowConfig();
        if (!StringUtils.hasText(flowConfig) || "{}".equals(flowConfig.trim())) {
            return null;
        }
        return workflow;
    }

    /**
     * advanced 编排回复：执行引擎产出最终文本与节点轨迹，轨迹随消息 meta 落库。
     * 引擎失败时消息内容为失败说明（已执行节点轨迹保留），会话可继续。
     */
    private GeneratedReply appendFlowAssistantMessage(AiConversationPo conversation, AiWorkflowPo workflow,
                                                      String userMessage) {
        AiFlowEngine.FlowResult result = aiFlowEngine.execute(workflow.getFlowConfig(), userMessage);
        String content = result.success()
                ? result.finalText()
                : "编排执行失败：" + result.errorMessage() + "\n可在右侧「执行信息」查看节点轨迹后重试。";

        AiChatMessagePo assistantMessage = new AiChatMessagePo();
        assistantMessage.setConversationId(conversation.getConversationId());
        assistantMessage.setRole(ROLE_ASSISTANT);
        assistantMessage.setContent(content);
        assistantMessage.setTokenCount(estimateTokenCount(content));
        assistantMessage.setSortOrder(nextSortOrder(conversation.getConversationId()));
        if (!result.traces().isEmpty()) {
            assistantMessage.setMeta(XuJsonUtil.toJsonString(Map.of("nodeTraces", result.traces())));
            assistantMessage.setNodeTraces(result.traces());
        }
        assistantMessage.setCreateTime(now());
        aiChatMessageMapper.insert(assistantMessage);
        refreshConversationCount(conversation);
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
        return appendUserMessage(conversationId, message, null);
    }

    private AiChatMessagePo appendUserMessage(Long conversationId, String message, List<AiChatImageVo> images) {
        AiChatMessagePo userMessage = new AiChatMessagePo();
        userMessage.setConversationId(conversationId);
        userMessage.setRole(ROLE_USER);
        userMessage.setContent(message);
        userMessage.setTokenCount(estimateTokenCount(message));
        userMessage.setSortOrder(nextSortOrder(conversationId));
        if (images != null && !images.isEmpty()) {
            userMessage.setImages(XuJsonUtil.toJsonString(images));
            userMessage.setImageList(images);
        }
        userMessage.setCreateTime(now());
        aiChatMessageMapper.insert(userMessage);
        return userMessage;
    }

    private ModelReply buildAssistantReply(AiConversationPo conversation, ChatContext context, String userMessage,
                                           List<ScoredParagraph> hitParagraphs, List<String> pendingImageDataUrls) {
        AiModelPo model = resolveModel(context.modelId());
        ModelReply modelReply = tryBuildModelReply(conversation, context, model, hitParagraphs, pendingImageDataUrls);
        if (modelReply != null && StringUtils.hasText(modelReply.content())) {
            return modelReply;
        }
        return new ModelReply(buildFallbackAssistantReply(context, model, userMessage, hitParagraphs), List.of());
    }

    private ModelReply tryBuildModelReply(AiConversationPo conversation, ChatContext context, AiModelPo model,
                                          List<ScoredParagraph> hitParagraphs, List<String> pendingImageDataUrls) {
        if (model == null) {
            return null;
        }
        String apiKey = credentialResolver.resolveApiKey(model);
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        List<AiOpenAiCompatibleClient.ProviderMessage> messages =
                buildProviderMessages(conversation, context, hitParagraphs, pendingImageDataUrls);
        try {
            List<McpToolBinding> toolBindings = resolveMcpToolBindings(context.mcpServerIds());
            if (!toolBindings.isEmpty()) {
                return runToolCallChat(model, apiKey, messages, toolBindings);
            }
            String content = openAiCompatibleClient.chatCompletion(model, apiKey, messages, model.getMaxTokens());
            return new ModelReply(content, List.of());
        } catch (BusinessException ex) {
            log.warn("AI provider fallback triggered, provider={}, modelCode={}, conversationId={}, reason={}",
                    model.getProvider(), model.getModelCode(), conversation.getConversationId(), ex.getMessage());
            return null;
        }
    }

    /**
     * MCP 工具真实调用（function calling）：模型触发 tool_calls 时经 MCP 客户端执行，
     * 结果回填后二次请求，循环上限 {@link #MAX_TOOL_CALL_ROUNDS} 轮。
     */
    private ModelReply runToolCallChat(AiModelPo model, String apiKey,
                                       List<AiOpenAiCompatibleClient.ProviderMessage> messages,
                                       List<McpToolBinding> toolBindings) {
        Map<String, McpToolBinding> bindingMap = new HashMap<>();
        List<AiOpenAiCompatibleClient.ToolSpec> toolSpecs = new ArrayList<>();
        for (McpToolBinding binding : toolBindings) {
            bindingMap.put(binding.functionName(), binding);
            toolSpecs.add(new AiOpenAiCompatibleClient.ToolSpec(
                    binding.functionName(), binding.description(), binding.inputSchema()));
        }
        AiOpenAiCompatibleClient.ToolLoopResult loopResult = openAiCompatibleClient.chatCompletionWithTools(
                model, apiKey, messages, model.getMaxTokens(), toolSpecs,
                (functionName, argumentsJson) -> executeMcpTool(bindingMap.get(functionName), argumentsJson),
                MAX_TOOL_CALL_ROUNDS);
        List<AiChatToolTraceVo> traces = new ArrayList<>();
        for (AiOpenAiCompatibleClient.ExecutedToolCall call : loopResult.executedCalls()) {
            McpToolBinding binding = bindingMap.get(call.toolName());
            AiChatToolTraceVo trace = new AiChatToolTraceVo();
            if (binding != null) {
                trace.setMcpId(binding.server().getMcpId());
                trace.setServerName(binding.server().getServerName());
                trace.setTransportType(binding.server().getTransportType());
                trace.setToolName(binding.originalToolName());
            } else {
                trace.setToolName(call.toolName());
            }
            trace.setCallArgs(excerpt(call.argumentsJson(), 400));
            trace.setCallResult(excerpt(call.result(), 400));
            trace.setCostMs(call.costMs());
            trace.setCallStatus(call.success() ? "succeeded" : "failed");
            trace.setSummary((binding != null ? binding.server().getServerName() + " · " : "")
                    + (binding != null ? binding.originalToolName() : call.toolName())
                    + (call.success() ? " 调用成功" : " 调用失败") + "（" + call.costMs() + "ms）");
            traces.add(trace);
        }
        return new ModelReply(loopResult.content(), traces);
    }

    private AiOpenAiCompatibleClient.ToolExecution executeMcpTool(McpToolBinding binding, String argumentsJson) {
        if (binding == null) {
            return new AiOpenAiCompatibleClient.ToolExecution("未知工具", false);
        }
        Map<String, Object> arguments = Map.of();
        if (StringUtils.hasText(argumentsJson)) {
            try {
                Map<String, Object> parsed = XuJsonUtil.parseObject(argumentsJson,
                        new TypeReference<Map<String, Object>>() {});
                arguments = parsed != null ? parsed : Map.of();
            } catch (RuntimeException ex) {
                return new AiOpenAiCompatibleClient.ToolExecution("工具入参不是合法 JSON：" + argumentsJson, false);
            }
        }
        try {
            String result = aiMcpClientService.callTool(binding.server(), binding.originalToolName(), arguments);
            return new AiOpenAiCompatibleClient.ToolExecution(result, true);
        } catch (BusinessException ex) {
            return new AiOpenAiCompatibleClient.ToolExecution(ex.getMessage(), false);
        } catch (RuntimeException ex) {
            log.warn("MCP tool execution error, server={}, tool={}",
                    binding.server().getServerName(), binding.originalToolName(), ex);
            return new AiOpenAiCompatibleClient.ToolExecution("工具执行异常", false);
        }
    }

    /**
     * 汇总会话绑定 MCP 服务的可调用工具：仅纳入已刷新出 inputSchema 的真实工具
     * （手填/占位工具缺 schema，不进模型工具清单，需先在 MCP 管理页「刷新工具」）。
     * function name 规则：mcp{mcpId}_{工具名净化}，回调时按绑定关系还原分发。
     */
    private List<McpToolBinding> resolveMcpToolBindings(List<Long> mcpServerIds) {
        if (mcpServerIds == null || mcpServerIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<AiMcpServerPo> wrapper = new LambdaQueryWrapper<AiMcpServerPo>()
                .in(AiMcpServerPo::getMcpId, mcpServerIds)
                .eq(AiMcpServerPo::getStatus, "0");
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiMcpServerPo::getTenantId, tenantId);
        }
        List<McpToolBinding> bindings = new ArrayList<>();
        for (AiMcpServerPo server : aiMcpServerMapper.selectList(wrapper)) {
            if ("stdio".equals(server.getTransportType()) || !StringUtils.hasText(server.getTools())) {
                continue;
            }
            List<Map<String, Object>> tools;
            try {
                tools = XuJsonUtil.parseObject(server.getTools(), new TypeReference<List<Map<String, Object>>>() {});
            } catch (RuntimeException ignored) {
                continue;
            }
            if (tools == null) {
                continue;
            }
            for (Map<String, Object> tool : tools) {
                Object name = tool != null ? tool.get("name") : null;
                Object schema = tool != null ? tool.get("inputSchema") : null;
                if (name == null || !StringUtils.hasText(String.valueOf(name)) || !(schema instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> inputSchema = new HashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) schema).entrySet()) {
                    inputSchema.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                String originalName = String.valueOf(name);
                Object description = tool.get("description");
                bindings.add(new McpToolBinding(
                        buildFunctionName(server.getMcpId(), originalName),
                        originalName,
                        description != null ? String.valueOf(description) : "",
                        inputSchema,
                        server));
            }
        }
        return bindings;
    }

    /**
     * OpenAI 兼容 function name 约束 [a-zA-Z0-9_-]{1,64}：非法字符替换为下划线并截断。
     */
    private String buildFunctionName(Long mcpId, String toolName) {
        String sanitized = toolName.replaceAll("[^a-zA-Z0-9_-]", "_");
        String functionName = "mcp" + mcpId + "_" + sanitized;
        return functionName.length() > 64 ? functionName.substring(0, 64) : functionName;
    }

    private record McpToolBinding(String functionName, String originalToolName, String description,
                                  Map<String, Object> inputSchema, AiMcpServerPo server) {
    }

    private record ModelReply(String content, List<AiChatToolTraceVo> toolTraces) {
    }

    private List<AiOpenAiCompatibleClient.ProviderMessage> buildProviderMessages(AiConversationPo conversation,
                                                                                 ChatContext context,
                                                                                 List<ScoredParagraph> hitParagraphs,
                                                                                 List<String> pendingImageDataUrls) {
        List<AiOpenAiCompatibleClient.ProviderMessage> messages = new ArrayList<>();
        String systemPrompt = buildSystemPrompt(context, hitParagraphs);
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(AiOpenAiCompatibleClient.ProviderMessage.system(systemPrompt));
        }

        List<AiChatMessagePo> history = selectMessages(conversation.getConversationId());
        int lastUserIndex = -1;
        for (int index = history.size() - 1; index >= 0; index--) {
            if (ROLE_USER.equals(history.get(index).getRole())) {
                lastUserIndex = index;
                break;
            }
        }
        int startIndex = Math.max(0, history.size() - HISTORY_MESSAGE_LIMIT);
        for (int index = startIndex; index < history.size(); index++) {
            AiChatMessagePo historyMessage = history.get(index);
            if (!StringUtils.hasText(historyMessage.getContent())) {
                continue;
            }
            if (ROLE_USER.equals(historyMessage.getRole())) {
                // 图片仅随本轮消息注入（base64），历史带图消息以文本形式承接，控制 token 消耗
                if (index == lastUserIndex && pendingImageDataUrls != null && !pendingImageDataUrls.isEmpty()) {
                    messages.add(AiOpenAiCompatibleClient.ProviderMessage.user(historyMessage.getContent(), pendingImageDataUrls));
                } else {
                    messages.add(AiOpenAiCompatibleClient.ProviderMessage.user(historyMessage.getContent()));
                }
            } else if (ROLE_ASSISTANT.equals(historyMessage.getRole())) {
                messages.add(AiOpenAiCompatibleClient.ProviderMessage.assistant(historyMessage.getContent()));
            }
        }
        if (messages.isEmpty()) {
            messages.add(AiOpenAiCompatibleClient.ProviderMessage.user("你好"));
        }
        return messages;
    }

    private String buildSystemPrompt(ChatContext context, List<ScoredParagraph> hitParagraphs) {
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
            for (ScoredParagraph hit : hitParagraphs) {
                builder.append(index++)
                        .append(". ")
                        .append(excerpt(hit.paragraph().getContent(), 200))
                        .append('\n');
            }
            builder.append("回答中直接引用了上述某条知识时，请在对应句子末尾以 [n] 形式标注引用编号")
                    .append("（n 为上面的条目序号，如 [1]），未引用的内容不要标注。\n");
        }
        return builder.toString().trim();
    }

    private String buildFallbackAssistantReply(ChatContext context, AiModelPo model, String userMessage,
                                               List<ScoredParagraph> hitParagraphs) {
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
            for (ScoredParagraph hit : hitParagraphs) {
                builder.append(index++)
                        .append(". ")
                        .append(excerpt(hit.paragraph().getContent(), 120))
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

    /**
     * RAG 检索：向量优先（知识库绑定 EMBEDDING 模型），关键词回退，统一走公共检索服务。
     */
    private List<ScoredParagraph> searchKnowledgeParagraphs(List<Long> knowledgeBaseIds, String userMessage) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || !StringUtils.hasText(userMessage)) {
            return List.of();
        }
        return knowledgeRetrievalService.retrieve(knowledgeBaseIds, userMessage.trim(), 5);
    }

    private List<AiChatMessagePo> enrichConversationMessages(List<AiChatMessagePo> messages, ChatContext context) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        String latestUserPrompt = null;
        for (AiChatMessagePo message : messages) {
            fillImageList(message);
            fillNodeTraces(message);
            fillToolCallTraces(message);
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
                                        List<ScoredParagraph> hitParagraphs) {
        if (assistantMessage == null || !ROLE_ASSISTANT.equals(assistantMessage.getRole())) {
            return;
        }
        assistantMessage.setKnowledgeSources(buildKnowledgeSources(context.knowledgeBaseIds(), userPrompt, hitParagraphs));
        // 已带真实调用轨迹（本轮工具循环或 meta 回显）时不再用配置摘要覆盖
        if (assistantMessage.getToolExecutions() == null || assistantMessage.getToolExecutions().isEmpty()) {
            assistantMessage.setToolExecutions(buildToolTraceSummaries(context.mcpServerIds()));
        }
    }

    /**
     * 解析消息 images JSON 为附件列表（前端渲染用）。
     */
    private void fillImageList(AiChatMessagePo message) {
        if (message == null || message.getImageList() != null || !StringUtils.hasText(message.getImages())) {
            return;
        }
        try {
            List<AiChatImageVo> images = XuJsonUtil.parseObject(message.getImages(),
                    new TypeReference<List<AiChatImageVo>>() {});
            message.setImageList(images != null ? images : List.of());
        } catch (RuntimeException ignored) {
            message.setImageList(List.of());
        }
    }

    /**
     * 解析消息 meta JSON 中的真实工具调用轨迹（历史回显用）。
     */
    private void fillToolCallTraces(AiChatMessagePo message) {
        if (message == null || !StringUtils.hasText(message.getMeta())
                || (message.getToolExecutions() != null && !message.getToolExecutions().isEmpty())) {
            return;
        }
        try {
            Map<String, Object> meta = XuJsonUtil.parseObject(message.getMeta(),
                    new TypeReference<Map<String, Object>>() {});
            Object rawCalls = meta != null ? meta.get("toolCalls") : null;
            if (rawCalls == null) {
                return;
            }
            List<AiChatToolTraceVo> traces = XuJsonUtil.parseObject(XuJsonUtil.toJsonString(rawCalls),
                    new TypeReference<List<AiChatToolTraceVo>>() {});
            if (traces != null && !traces.isEmpty()) {
                message.setToolExecutions(traces);
            }
        } catch (RuntimeException ignored) {
            // meta 解析失败时静默降级为配置摘要
        }
    }

    /**
     * 解析消息 meta JSON 中的编排节点轨迹（前端执行时间线用）。
     */
    private void fillNodeTraces(AiChatMessagePo message) {
        if (message == null || message.getNodeTraces() != null || !StringUtils.hasText(message.getMeta())) {
            return;
        }
        try {
            Map<String, Object> meta = XuJsonUtil.parseObject(message.getMeta(),
                    new TypeReference<Map<String, Object>>() {});
            Object rawTraces = meta != null ? meta.get("nodeTraces") : null;
            if (rawTraces == null) {
                return;
            }
            List<AiFlowNodeTraceVo> traces = XuJsonUtil.parseObject(XuJsonUtil.toJsonString(rawTraces),
                    new TypeReference<List<AiFlowNodeTraceVo>>() {});
            message.setNodeTraces(traces != null ? traces : List.of());
        } catch (RuntimeException ignored) {
            message.setNodeTraces(List.of());
        }
    }

    private List<AiChatKnowledgeSourceVo> buildKnowledgeSources(List<Long> knowledgeBaseIds, String userPrompt) {
        return buildKnowledgeSources(knowledgeBaseIds, userPrompt, null);
    }

    private List<AiChatKnowledgeSourceVo> buildKnowledgeSources(List<Long> knowledgeBaseIds, String userPrompt,
                                                                List<ScoredParagraph> precomputedHits) {
        List<ScoredParagraph> hits = precomputedHits != null
                ? precomputedHits
                : searchKnowledgeParagraphs(knowledgeBaseIds, userPrompt);
        if (hits.isEmpty()) {
            return List.of();
        }
        Map<Long, AiKnowledgeBasePo> knowledgeBaseMap = loadKnowledgeBaseMap(knowledgeBaseIds);
        List<AiChatKnowledgeSourceVo> results = new ArrayList<>();
        for (ScoredParagraph hit : hits) {
            AiParagraphPo paragraph = hit.paragraph();
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
            source.setScore(hit.score());
            source.setRetrievalType(hit.retrievalType());
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
        if (assistantMessage.getImageList() != null && !assistantMessage.getImageList().isEmpty()) {
            meta.put("images", assistantMessage.getImageList());
        }
        if (assistantMessage.getNodeTraces() != null && !assistantMessage.getNodeTraces().isEmpty()) {
            meta.put("nodeTraces", assistantMessage.getNodeTraces());
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
        if (agent == null || (agent.getDelFlag() != null && agent.getDelFlag() != 0)) {
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

    /**
     * 本轮消息的图片附件：items 落库回显，dataUrls 注入模型。
     */
    private record LoadedImages(List<AiChatImageVo> items, List<String> dataUrls) {
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
