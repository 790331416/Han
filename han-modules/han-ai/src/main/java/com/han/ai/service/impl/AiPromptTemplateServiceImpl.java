package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.ai.domain.po.AiPromptTemplatePo;
import com.han.ai.domain.query.AiPromptTemplateQuery;
import com.han.ai.mapper.AiPromptTemplateMapper;
import com.han.ai.service.IAiPromptTemplateService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Prompt template service implementation.
 */
@Service
@RequiredArgsConstructor
public class AiPromptTemplateServiceImpl extends AiServiceSupport implements IAiPromptTemplateService {

    private final AiPromptTemplateMapper aiPromptTemplateMapper;

    @Override
    public PageResult<AiPromptTemplatePo> selectPage(AiPromptTemplateQuery query) {
        AiPromptTemplateQuery safeQuery = query != null ? query : new AiPromptTemplateQuery();
        int pageNum = normalizePageNum(safeQuery.getPageNum());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        Page<AiPromptTemplatePo> page = aiPromptTemplateMapper.selectPage(new Page<>(pageNum, pageSize), buildQueryWrapper(safeQuery));
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public AiPromptTemplatePo selectById(Long templateId) {
        return requireExisting(templateId);
    }

    @Override
    public List<AiPromptTemplatePo> selectAll() {
        LambdaQueryWrapper<AiPromptTemplatePo> wrapper = new LambdaQueryWrapper<AiPromptTemplatePo>()
                .eq(AiPromptTemplatePo::getStatus, STATUS_ENABLED)
                .orderByAsc(AiPromptTemplatePo::getCategory)
                .orderByAsc(AiPromptTemplatePo::getTemplateName);
        applyTenantScope(wrapper);
        return aiPromptTemplateMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(AiPromptTemplatePo template) {
        validateForSave(template, false);
        ensureTemplateNameUnique(template.getTemplateName(), null);
        normalize(template);
        fillCreateAudit(template);
        template.setBuiltIn(template.getBuiltIn() != null ? template.getBuiltIn() : 0);
        aiPromptTemplateMapper.insert(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(AiPromptTemplatePo template) {
        if (template == null || template.getTemplateId() == null) {
            throw new BusinessException("模板ID不能为空");
        }
        AiPromptTemplatePo existing = requireExisting(template.getTemplateId());
        if (existing.getBuiltIn() != null && existing.getBuiltIn() == 1) {
            throw new BusinessException("Built-in prompt template cannot be edited");
        }
        copyEditableFields(template, existing);
        validateForSave(existing, true);
        ensureTemplateNameUnique(existing.getTemplateName(), existing.getTemplateId());
        normalize(existing);
        fillUpdateAudit(existing);
        aiPromptTemplateMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long templateId) {
        AiPromptTemplatePo template = requireExisting(templateId);
        if (template.getBuiltIn() != null && template.getBuiltIn() == 1) {
            throw new BusinessException("内置模板不允许删除");
        }
        aiPromptTemplateMapper.deleteById(templateId);
    }

    @Override
    public String render(Long templateId, Map<String, String> variables) {
        AiPromptTemplatePo template = requireExisting(templateId);
        String rendered = template.getContent();
        if (!StringUtils.hasText(rendered)) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            return rendered;
        }
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue();
            rendered = rendered.replace("{{" + key + "}}", value);
        }
        return rendered;
    }

    private LambdaQueryWrapper<AiPromptTemplatePo> buildQueryWrapper(AiPromptTemplateQuery query) {
        LambdaQueryWrapper<AiPromptTemplatePo> wrapper = new LambdaQueryWrapper<AiPromptTemplatePo>()
                .like(StringUtils.hasText(query.getTemplateName()), AiPromptTemplatePo::getTemplateName, query.getTemplateName())
                .eq(StringUtils.hasText(query.getCategory()), AiPromptTemplatePo::getCategory, query.getCategory())
                .eq(StringUtils.hasText(query.getStatus()), AiPromptTemplatePo::getStatus, query.getStatus())
                .orderByDesc(AiPromptTemplatePo::getBuiltIn)
                .orderByDesc(AiPromptTemplatePo::getUpdateTime)
                .orderByDesc(AiPromptTemplatePo::getCreateTime);
        applyTenantScope(wrapper);
        return wrapper;
    }

    private void applyTenantScope(LambdaQueryWrapper<AiPromptTemplatePo> wrapper) {
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.and(q -> q.eq(AiPromptTemplatePo::getTenantId, tenantId).or().eq(AiPromptTemplatePo::getBuiltIn, 1));
        }
    }

    private AiPromptTemplatePo requireExisting(Long templateId) {
        if (templateId == null) {
            throw new BusinessException("模板ID不能为空");
        }
        AiPromptTemplatePo template = aiPromptTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        Long tenantId = currentTenantId();
        if (tenantId != null && (template.getBuiltIn() == null || template.getBuiltIn() != 1)
                && !tenantId.equals(template.getTenantId())) {
            throw new BusinessException("无权访问该模板");
        }
        return template;
    }

    private void validateForSave(AiPromptTemplatePo template, boolean update) {
        if (template == null) {
            throw new BusinessException("模板信息不能为空");
        }
        if (update && template.getTemplateId() == null) {
            throw new BusinessException("模板ID不能为空");
        }
        if (!StringUtils.hasText(template.getTemplateName())) {
            throw new BusinessException("模板名称不能为空");
        }
        if (!StringUtils.hasText(template.getCategory())) {
            throw new BusinessException("模板分类不能为空");
        }
        if (!StringUtils.hasText(template.getContent())) {
            throw new BusinessException("模板内容不能为空");
        }
        if (!STATUS_ENABLED.equals(template.getStatus()) && !STATUS_DISABLED.equals(template.getStatus())) {
            throw new BusinessException("模板状态不合法");
        }
    }

    private void ensureTemplateNameUnique(String templateName, Long excludeId) {
        LambdaQueryWrapper<AiPromptTemplatePo> wrapper = new LambdaQueryWrapper<AiPromptTemplatePo>()
                .eq(AiPromptTemplatePo::getTemplateName, templateName);
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiPromptTemplatePo::getTenantId, tenantId);
        }
        if (excludeId != null) {
            wrapper.ne(AiPromptTemplatePo::getTemplateId, excludeId);
        }
        if (aiPromptTemplateMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("模板名称已存在");
        }
    }

    private void copyEditableFields(AiPromptTemplatePo source, AiPromptTemplatePo target) {
        target.setTemplateName(source.getTemplateName());
        target.setCategory(source.getCategory());
        target.setContent(source.getContent());
        target.setVariables(source.getVariables());
        target.setDescription(source.getDescription());
        target.setStatus(source.getStatus());
    }

    private void normalize(AiPromptTemplatePo template) {
        template.setTemplateName(trimToNull(template.getTemplateName()));
        template.setCategory(trimToNull(template.getCategory()));
        template.setContent(trimToNull(template.getContent()));
        template.setVariables(trimToEmpty(template.getVariables()));
        template.setDescription(trimToNull(template.getDescription()));
        template.setStatus(StringUtils.hasText(template.getStatus()) ? template.getStatus().trim() : STATUS_ENABLED);
    }

    private void fillCreateAudit(AiPromptTemplatePo template) {
        template.setTenantId(resolveTenantIdForWrite());
        template.setCreateBy(resolveOperator());
        template.setCreateTime(now());
        template.setUpdateBy(resolveOperator());
        template.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiPromptTemplatePo template) {
        template.setUpdateBy(resolveOperator());
        template.setUpdateTime(now());
    }
}
