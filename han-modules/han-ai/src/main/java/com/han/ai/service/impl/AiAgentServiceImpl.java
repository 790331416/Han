package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.ai.domain.po.AiAgentPo;
import com.han.ai.domain.query.AiAgentQuery;
import com.han.ai.mapper.AiAgentMapper;
import com.han.ai.service.IAiAgentService;
import com.han.ai.service.IAiChatService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;

/**
 * AI 智能体服务实现。
 */
@Service
@RequiredArgsConstructor
public class AiAgentServiceImpl extends AiServiceSupport implements IAiAgentService {

    private static final SecureRandom SHARE_KEY_RANDOM = new SecureRandom();
    private static final String SHARE_KEY_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SHARE_KEY_LENGTH = 32;

    private final AiAgentMapper aiAgentMapper;
    private final IAiChatService aiChatService;

    @Override
    public PageResult<AiAgentPo> selectPage(AiAgentQuery query) {
        AiAgentQuery safeQuery = query != null ? query : new AiAgentQuery();
        int pageNum = normalizePageNum(safeQuery.getPageNum());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        Page<AiAgentPo> page = aiAgentMapper.selectPage(new Page<>(pageNum, pageSize), buildQueryWrapper(safeQuery));
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public AiAgentPo selectById(Long agentId) {
        return requireExisting(agentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(AiAgentPo agent) {
        validateForSave(agent, false);
        ensureAgentNameUnique(agent.getAgentName(), null);
        normalize(agent);
        fillCreateAudit(agent);
        agent.setDelFlag(0);
        aiAgentMapper.insert(agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(AiAgentPo agent) {
        if (agent == null || agent.getAgentId() == null) {
            throw new BusinessException("智能体ID不能为空");
        }
        AiAgentPo existing = requireExisting(agent.getAgentId());
        copyEditableFields(agent, existing);
        validateForSave(existing, true);
        ensureAgentNameUnique(existing.getAgentName(), existing.getAgentId());
        normalize(existing);
        fillUpdateAudit(existing);
        aiAgentMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long agentId) {
        AiAgentPo agent = requireExisting(agentId);
        agent.setDelFlag(1);
        fillUpdateAudit(agent);
        aiAgentMapper.updateById(agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long agentId) {
        AiAgentPo agent = requireExisting(agentId);
        agent.setPublishedRaw("1");
        // 发布补生成分享 key（已有则保留，重置走 resetShareKey）
        if (!StringUtils.hasText(agent.getShareKey())) {
            agent.setShareKey(generateShareKey());
        }
        fillUpdateAudit(agent);
        aiAgentMapper.updateById(agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpublish(Long agentId) {
        AiAgentPo agent = requireExisting(agentId);
        agent.setPublishedRaw("0");
        fillUpdateAudit(agent);
        aiAgentMapper.updateById(agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetShareKey(Long agentId) {
        AiAgentPo agent = requireExisting(agentId);
        agent.setShareKey(generateShareKey());
        fillUpdateAudit(agent);
        aiAgentMapper.updateById(agent);
        return agent.getShareKey();
    }

    @Override
    public AiAgentPo selectPublishedByShareKey(String shareKey) {
        if (!StringUtils.hasText(shareKey)) {
            return null;
        }
        AiAgentPo agent = aiAgentMapper.selectOne(new LambdaQueryWrapper<AiAgentPo>()
                .eq(AiAgentPo::getShareKey, shareKey.trim())
                .eq(AiAgentPo::getDelFlag, 0)
                .last("LIMIT 1"));
        if (agent == null || !"1".equals(agent.getPublishedRaw()) || !STATUS_ENABLED.equals(agent.getStatus())) {
            return null;
        }
        return agent;
    }

    @Override
    public String chat(Long agentId, String message, Long conversationId) {
        return aiChatService.chatWithAgent(agentId, message, conversationId);
    }

    private String generateShareKey() {
        StringBuilder builder = new StringBuilder(SHARE_KEY_LENGTH);
        for (int i = 0; i < SHARE_KEY_LENGTH; i++) {
            builder.append(SHARE_KEY_ALPHABET.charAt(SHARE_KEY_RANDOM.nextInt(SHARE_KEY_ALPHABET.length())));
        }
        return builder.toString();
    }

    private LambdaQueryWrapper<AiAgentPo> buildQueryWrapper(AiAgentQuery query) {
        LambdaQueryWrapper<AiAgentPo> wrapper = new LambdaQueryWrapper<AiAgentPo>()
                .eq(AiAgentPo::getDelFlag, 0)
                .like(StringUtils.hasText(query.getAgentName()), AiAgentPo::getAgentName, query.getAgentName())
                .eq(query.getPublished() != null, AiAgentPo::getPublishedRaw, Boolean.TRUE.equals(query.getPublished()) ? "1" : "0")
                .eq(StringUtils.hasText(query.getStatus()), AiAgentPo::getStatus, query.getStatus())
                .orderByDesc(AiAgentPo::getUpdateTime)
                .orderByDesc(AiAgentPo::getCreateTime);
        applyTenantScope(wrapper);
        return wrapper;
    }

    private void applyTenantScope(LambdaQueryWrapper<AiAgentPo> wrapper) {
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiAgentPo::getTenantId, tenantId);
        }
    }

    private AiAgentPo requireExisting(Long agentId) {
        if (agentId == null) {
            throw new BusinessException("智能体ID不能为空");
        }
        AiAgentPo agent = aiAgentMapper.selectById(agentId);
        if (agent == null || (agent.getDelFlag() != null && agent.getDelFlag() != 0)) {
            throw new BusinessException("智能体不存在");
        }
        Long tenantId = currentTenantId();
        if (tenantId != null && !tenantId.equals(agent.getTenantId())) {
            throw new BusinessException("无权访问该智能体");
        }
        return agent;
    }

    private void validateForSave(AiAgentPo agent, boolean update) {
        if (agent == null) {
            throw new BusinessException("智能体信息不能为空");
        }
        if (update && agent.getAgentId() == null) {
            throw new BusinessException("智能体ID不能为空");
        }
        if (!StringUtils.hasText(agent.getAgentName())) {
            throw new BusinessException("智能体名称不能为空");
        }
        if (agent.getModelId() == null) {
            throw new BusinessException("AI模型不能为空");
        }
        if (agent.getMaxTokens() == null || agent.getMaxTokens() < 1) {
            throw new BusinessException("最大Token必须大于0");
        }
        if (agent.getTemperature() == null) {
            agent.setTemperature(BigDecimal.valueOf(0.7D));
        }
        if (agent.getTemperature().compareTo(BigDecimal.ZERO) < 0
                || agent.getTemperature().compareTo(BigDecimal.valueOf(2D)) > 0) {
            throw new BusinessException("温度必须在0到2之间");
        }
        if (agent.getHistoryLimit() != null && (agent.getHistoryLimit() < 1 || agent.getHistoryLimit() > 100)) {
            throw new BusinessException("对话历史条数必须在1到100之间");
        }
        if (agent.getRetrievalTopK() != null && (agent.getRetrievalTopK() < 1 || agent.getRetrievalTopK() > 20)) {
            throw new BusinessException("知识库检索条数必须在1到20之间");
        }
        if (agent.getSimilarityThreshold() != null
                && (agent.getSimilarityThreshold().compareTo(BigDecimal.ZERO) < 0
                || agent.getSimilarityThreshold().compareTo(BigDecimal.ONE) > 0)) {
            throw new BusinessException("相似度阈值必须在0到1之间");
        }
        validateSuggestedQuestions(agent.getSuggestedQuestions());
        if (!STATUS_ENABLED.equals(agent.getStatus()) && !STATUS_DISABLED.equals(agent.getStatus())) {
            throw new BusinessException("智能体状态不合法");
        }
    }

    private void ensureAgentNameUnique(String agentName, Long excludeId) {
        LambdaQueryWrapper<AiAgentPo> wrapper = new LambdaQueryWrapper<AiAgentPo>()
                .eq(AiAgentPo::getDelFlag, 0)
                .eq(AiAgentPo::getAgentName, agentName);
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiAgentPo::getTenantId, tenantId);
        }
        if (excludeId != null) {
            wrapper.ne(AiAgentPo::getAgentId, excludeId);
        }
        if (aiAgentMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("智能体名称已存在");
        }
    }

    /**
     * 合并请求体到库内现值：未提交的字段保持原样（见 {@link AiServiceSupport#copyIfPresent}）。
     */
    private void copyEditableFields(AiAgentPo source, AiAgentPo target) {
        copyIfPresent(source.getAgentName(), target::setAgentName);
        copyIfPresent(source.getDescription(), target::setDescription);
        copyIfPresent(source.getAvatar(), target::setAvatar);
        copyIfPresent(source.getSystemPrompt(), target::setSystemPrompt);
        copyIfPresent(source.getPrologue(), target::setPrologue);
        copyIfPresent(source.getSuggestedQuestions(), target::setSuggestedQuestions);
        copyIfPresent(source.getModelId(), target::setModelId);
        copyIfPresent(source.getKnowledgeBaseIds(), target::setKnowledgeBaseIds);
        copyIfPresent(source.getMcpServerIds(), target::setMcpServerIds);
        copyIfPresent(source.getTemperature(), target::setTemperature);
        copyIfPresent(source.getMaxTokens(), target::setMaxTokens);
        // 以下三个调参列声明为 FieldStrategy.ALWAYS，NULL 即恢复默认（编辑页「留空按默认」）。
        // 保留无条件回写以守住显式置空能力；代价是这三个字段不支持部分提交，
        // 新增部分更新调用方时必须整体带上它们。
        target.setHistoryLimit(source.getHistoryLimit());
        target.setRetrievalTopK(source.getRetrievalTopK());
        target.setSimilarityThreshold(source.getSimilarityThreshold());
        copyIfPresent(source.getStatus(), target::setStatus);
    }

    private void normalize(AiAgentPo agent) {
        agent.setAgentName(trimToNull(agent.getAgentName()));
        agent.setDescription(trimToNull(agent.getDescription()));
        agent.setAvatar(trimToEmpty(agent.getAvatar()));
        agent.setSystemPrompt(trimToEmpty(agent.getSystemPrompt()));
        agent.setPrologue(trimToEmpty(agent.getPrologue()));
        agent.setSuggestedQuestions(StringUtils.hasText(agent.getSuggestedQuestions()) ? agent.getSuggestedQuestions().trim() : "[]");
        agent.setKnowledgeBaseIds(StringUtils.hasText(agent.getKnowledgeBaseIds()) ? agent.getKnowledgeBaseIds().trim() : "[]");
        agent.setMcpServerIds(StringUtils.hasText(agent.getMcpServerIds()) ? agent.getMcpServerIds().trim() : "[]");
        agent.setStatus(StringUtils.hasText(agent.getStatus()) ? agent.getStatus().trim() : STATUS_ENABLED);
        if (!StringUtils.hasText(agent.getPublishedRaw())) {
            agent.setPublishedRaw("0");
        }
    }

    private void fillCreateAudit(AiAgentPo agent) {
        agent.setTenantId(resolveTenantIdForWrite());
        agent.setCreateBy(resolveOperator());
        agent.setCreateTime(now());
        agent.setUpdateBy(resolveOperator());
        agent.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiAgentPo agent) {
        agent.setUpdateBy(resolveOperator());
        agent.setUpdateTime(now());
    }
}
