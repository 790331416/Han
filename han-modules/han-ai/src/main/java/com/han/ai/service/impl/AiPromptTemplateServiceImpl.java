package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.ai.domain.po.AiPromptTemplatePo;
import com.han.ai.domain.query.AiPromptTemplateQuery;
import com.han.ai.mapper.AiPromptTemplateMapper;
import com.han.ai.service.IAiPromptTemplateService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.mybatis.helper.TenantHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 提示词模板服务实现。
 */
@Service
@RequiredArgsConstructor
public class AiPromptTemplateServiceImpl extends AiServiceSupport implements IAiPromptTemplateService {

    private final AiPromptTemplateMapper aiPromptTemplateMapper;

    @Override
    public PageResult<AiPromptTemplatePo> selectPage(AiPromptTemplateQuery query) {
        ensureBuiltInTemplates();
        AiPromptTemplateQuery safeQuery = query != null ? query : new AiPromptTemplateQuery();
        int pageNum = normalizePageNum(safeQuery.getPageNum());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        Page<AiPromptTemplatePo> page = TenantHelper.ignore(() ->
                aiPromptTemplateMapper.selectPage(new Page<>(pageNum, pageSize), buildQueryWrapper(safeQuery)));
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public AiPromptTemplatePo selectById(Long templateId) {
        return requireExisting(templateId);
    }

    @Override
    public List<AiPromptTemplatePo> selectAll() {
        ensureBuiltInTemplates();
        LambdaQueryWrapper<AiPromptTemplatePo> wrapper = new LambdaQueryWrapper<AiPromptTemplatePo>()
                .eq(AiPromptTemplatePo::getStatus, STATUS_ENABLED)
                .orderByAsc(AiPromptTemplatePo::getCategory)
                .orderByAsc(AiPromptTemplatePo::getTemplateName);
        applyTenantScope(wrapper);
        return TenantHelper.ignore(() -> aiPromptTemplateMapper.selectList(wrapper));
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
        TenantHelper.ignore(() -> aiPromptTemplateMapper.updateById(existing));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long templateId) {
        AiPromptTemplatePo template = requireExisting(templateId);
        if (template.getBuiltIn() != null && template.getBuiltIn() == 1) {
            throw new BusinessException("内置模板不允许删除");
        }
        TenantHelper.ignore(() -> aiPromptTemplateMapper.deleteById(templateId));
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

    private void ensureBuiltInTemplates() {
        TenantHelper.ignore(() -> {
            for (AiPromptTemplateBuiltinRegistry.Seed seed : AiPromptTemplateBuiltinRegistry.all()) {
                upsertBuiltInTemplate(seed);
            }
            return null;
        });
    }

    private void upsertBuiltInTemplate(AiPromptTemplateBuiltinRegistry.Seed seed) {
        LambdaQueryWrapper<AiPromptTemplatePo> wrapper = new LambdaQueryWrapper<AiPromptTemplatePo>()
                .eq(AiPromptTemplatePo::getTemplateName, seed.templateName());
        List<AiPromptTemplatePo> existingTemplates = aiPromptTemplateMapper.selectList(wrapper);
        if (existingTemplates == null || existingTemplates.isEmpty()) {
            aiPromptTemplateMapper.insert(toBuiltInTemplate(seed));
            return;
        }
        List<AiPromptTemplatePo> managedTemplates = existingTemplates.stream()
                .filter(this::isManagedBuiltInTemplate)
                .toList();
        if (managedTemplates.isEmpty()) {
            aiPromptTemplateMapper.insert(toBuiltInTemplate(seed));
            return;
        }
        for (AiPromptTemplatePo existing : managedTemplates) {
            if (shouldRefreshBuiltInTemplate(existing, seed)) {
                existing.setTenantId(0L);
                existing.setCategory(seed.category());
                existing.setContent(seed.content());
                existing.setVariables(seed.variables());
                existing.setDescription(seed.description());
                existing.setBuiltIn(1);
                existing.setStatus(STATUS_ENABLED);
                fillUpdateAudit(existing);
                aiPromptTemplateMapper.updateById(existing);
            }
        }
    }

    private AiPromptTemplatePo toBuiltInTemplate(AiPromptTemplateBuiltinRegistry.Seed seed) {
        AiPromptTemplatePo template = new AiPromptTemplatePo();
        template.setTenantId(0L);
        template.setTemplateName(seed.templateName());
        template.setCategory(seed.category());
        template.setContent(seed.content());
        template.setVariables(seed.variables());
        template.setDescription(seed.description());
        template.setBuiltIn(1);
        template.setStatus(STATUS_ENABLED);
        template.setCreateBy("system");
        template.setCreateTime(now());
        template.setUpdateBy("system");
        template.setUpdateTime(now());
        return template;
    }

    private boolean isManagedBuiltInTemplate(AiPromptTemplatePo template) {
        if (template == null) {
            return false;
        }
        return Objects.equals(template.getBuiltIn(), 1)
                || template.getTenantId() == null
                || Objects.equals(template.getTenantId(), 0L);
    }

    private boolean shouldRefreshBuiltInTemplate(AiPromptTemplatePo existing, AiPromptTemplateBuiltinRegistry.Seed seed) {
        return existing.getBuiltIn() == null || existing.getBuiltIn() != 1
                || !Objects.equals(existing.getTenantId(), 0L)
                || !STATUS_ENABLED.equals(existing.getStatus())
                || !Objects.equals(existing.getCategory(), seed.category())
                || !Objects.equals(existing.getContent(), seed.content())
                || !Objects.equals(existing.getVariables(), seed.variables())
                || !Objects.equals(existing.getDescription(), seed.description());
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
        AiPromptTemplatePo template = TenantHelper.ignore(() -> aiPromptTemplateMapper.selectById(templateId));
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
        if (TenantHelper.ignore(() -> aiPromptTemplateMapper.selectCount(wrapper)) > 0) {
            throw new BusinessException("模板名称已存在");
        }
    }

    /**
     * 合并请求体到库内现值：未提交的字段保持原样（见 {@link AiServiceSupport#copyIfPresent}）。
     */
    private void copyEditableFields(AiPromptTemplatePo source, AiPromptTemplatePo target) {
        copyIfPresent(source.getTemplateName(), target::setTemplateName);
        copyIfPresent(source.getCategory(), target::setCategory);
        copyIfPresent(source.getContent(), target::setContent);
        copyIfPresent(source.getVariables(), target::setVariables);
        copyIfPresent(source.getDescription(), target::setDescription);
        copyIfPresent(source.getStatus(), target::setStatus);
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
