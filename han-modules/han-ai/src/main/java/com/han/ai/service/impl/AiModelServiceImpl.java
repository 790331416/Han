package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.ai.domain.po.AiModelPo;
import com.han.ai.domain.query.AiModelQuery;
import com.han.ai.mapper.AiModelMapper;
import com.han.ai.service.IAiModelService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI model service implementation.
 */
@Service
@RequiredArgsConstructor
public class AiModelServiceImpl extends AiServiceSupport implements IAiModelService {

    private static final String MODEL_TYPE_IMAGE = "IMAGE";
    private static final String MODEL_TYPE_VIDEO = "VIDEO";

    private final AiModelMapper aiModelMapper;
    private final AiModelCredentialResolver credentialResolver;
    private final AiOpenAiCompatibleClient openAiCompatibleClient;

    @Override
    public PageResult<AiModelPo> selectPage(AiModelQuery query) {
        AiModelQuery safeQuery = query != null ? query : new AiModelQuery();
        int pageNum = normalizePageNum(safeQuery.getPageNum());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        Page<AiModelPo> page = aiModelMapper.selectPage(new Page<>(pageNum, pageSize), buildQueryWrapper(safeQuery));
        enrichCredentialMetadata(page.getRecords());
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public AiModelPo selectById(Long modelId) {
        AiModelPo model = requireExisting(modelId);
        enrichCredentialMetadata(model);
        return model;
    }

    @Override
    public List<AiModelPo> selectAll(String modelType) {
        LambdaQueryWrapper<AiModelPo> enabledWrapper = new LambdaQueryWrapper<AiModelPo>()
                .eq(AiModelPo::getStatus, STATUS_ENABLED)
                .eq(StringUtils.hasText(modelType), AiModelPo::getModelType, modelType)
                .orderByAsc(AiModelPo::getModelName);
        applyTenantScope(enabledWrapper);
        List<AiModelPo> enabledModels = aiModelMapper.selectList(enabledWrapper);
        if (!enabledModels.isEmpty()) {
            enrichCredentialMetadata(enabledModels);
            return enabledModels;
        }

        // Compatibility fallback for historical seed data that contains only disabled defaults.
        LambdaQueryWrapper<AiModelPo> fallbackWrapper = new LambdaQueryWrapper<AiModelPo>()
                .eq(StringUtils.hasText(modelType), AiModelPo::getModelType, modelType)
                .orderByAsc(AiModelPo::getStatus)
                .orderByAsc(AiModelPo::getModelName);
        applyTenantScope(fallbackWrapper);
        List<AiModelPo> fallbackModels = aiModelMapper.selectList(fallbackWrapper);
        enrichCredentialMetadata(fallbackModels);
        return fallbackModels;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(AiModelPo model) {
        validateForCreate(model);
        ensureModelNameUnique(model.getModelName(), null);
        normalize(model);
        fillCreateAudit(model);
        aiModelMapper.insert(model);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(AiModelPo model) {
        if (model == null || model.getModelId() == null) {
            throw new BusinessException("模型ID不能为空");
        }
        AiModelPo existing = requireExisting(model.getModelId());
        copyEditableFields(model, existing);
        validateForUpdate(existing);
        ensureModelNameUnique(existing.getModelName(), existing.getModelId());
        normalize(existing);
        fillUpdateAudit(existing);
        aiModelMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long modelId) {
        requireExisting(modelId);
        aiModelMapper.deleteById(modelId);
    }

    @Override
    public String test(Long modelId) {
        AiModelPo model = requireExisting(modelId);
        if (!STATUS_ENABLED.equals(model.getStatus())) {
            throw new BusinessException("当前模型已停用，无法测试");
        }
        if (MODEL_TYPE_IMAGE.equalsIgnoreCase(model.getModelType())) {
            return openAiCompatibleClient.testImageGeneration(model, credentialResolver.resolveApiKey(model));
        }
        if (MODEL_TYPE_VIDEO.equalsIgnoreCase(model.getModelType())) {
            return openAiCompatibleClient.testVideoConfiguration(model, credentialResolver.resolveApiKey(model));
        }
        return openAiCompatibleClient.testConnection(model, credentialResolver.resolveApiKey(model));
    }

    private LambdaQueryWrapper<AiModelPo> buildQueryWrapper(AiModelQuery query) {
        LambdaQueryWrapper<AiModelPo> wrapper = new LambdaQueryWrapper<AiModelPo>()
                .like(StringUtils.hasText(query.getModelName()), AiModelPo::getModelName, query.getModelName())
                .eq(StringUtils.hasText(query.getModelType()), AiModelPo::getModelType, query.getModelType())
                .eq(StringUtils.hasText(query.getProvider()), AiModelPo::getProvider, query.getProvider())
                .eq(StringUtils.hasText(query.getStatus()), AiModelPo::getStatus, query.getStatus())
                .orderByDesc(AiModelPo::getUpdateTime)
                .orderByDesc(AiModelPo::getCreateTime);
        applyTenantScope(wrapper);
        return wrapper;
    }

    private void applyTenantScope(LambdaQueryWrapper<AiModelPo> wrapper) {
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiModelPo::getTenantId, tenantId);
        }
    }

    private AiModelPo requireExisting(Long modelId) {
        if (modelId == null) {
            throw new BusinessException("模型ID不能为空");
        }
        AiModelPo model = aiModelMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        Long tenantId = currentTenantId();
        if (tenantId != null && !tenantId.equals(model.getTenantId())) {
            throw new BusinessException("无权访问该模型");
        }
        return model;
    }

    private void validateForCreate(AiModelPo model) {
        if (model == null) {
            throw new BusinessException("模型信息不能为空");
        }
        validateCoreFields(model);
    }

    private void validateForUpdate(AiModelPo model) {
        validateCoreFields(model);
    }

    private void validateCoreFields(AiModelPo model) {
        if (!StringUtils.hasText(model.getModelName())) {
            throw new BusinessException("模型名称不能为空");
        }
        if (!StringUtils.hasText(model.getModelType())) {
            throw new BusinessException("模型类型不能为空");
        }
        if (!StringUtils.hasText(model.getProvider())) {
            throw new BusinessException("供应商不能为空");
        }
        if (!StringUtils.hasText(model.getModelCode())) {
            throw new BusinessException("模型标识不能为空");
        }
        if (!StringUtils.hasText(model.getBaseUrl())) {
            throw new BusinessException("API Base URL不能为空");
        }
        if (model.getMaxTokens() == null || model.getMaxTokens() < 1) {
            throw new BusinessException("最大Token数必须大于0");
        }
        if (model.getTemperature() == null) {
            model.setTemperature(BigDecimal.valueOf(0.7D));
        }
        if (model.getTemperature().compareTo(BigDecimal.ZERO) < 0
                || model.getTemperature().compareTo(BigDecimal.valueOf(2D)) > 0) {
            throw new BusinessException("温度必须在0到2之间");
        }
        if (!STATUS_ENABLED.equals(model.getStatus()) && !STATUS_DISABLED.equals(model.getStatus())) {
            throw new BusinessException("模型状态不合法");
        }
    }

    private void ensureModelNameUnique(String modelName, Long excludeId) {
        LambdaQueryWrapper<AiModelPo> wrapper = new LambdaQueryWrapper<AiModelPo>()
                .eq(AiModelPo::getModelName, modelName);
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiModelPo::getTenantId, tenantId);
        }
        if (excludeId != null) {
            wrapper.ne(AiModelPo::getModelId, excludeId);
        }
        if (aiModelMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("模型名称已存在");
        }
    }

    private void copyEditableFields(AiModelPo source, AiModelPo target) {
        target.setModelName(source.getModelName());
        target.setModelType(source.getModelType());
        target.setProvider(source.getProvider());
        target.setModelCode(source.getModelCode());
        target.setBaseUrl(source.getBaseUrl());
        if (!credentialResolver.shouldKeepExistingApiKey(source.getApiKey())) {
            target.setApiKey(source.getApiKey());
        }
        target.setMaxTokens(source.getMaxTokens());
        target.setTemperature(source.getTemperature());
        target.setStatus(source.getStatus());
        target.setRemark(source.getRemark());
    }

    private void normalize(AiModelPo model) {
        model.setModelName(trimToNull(model.getModelName()));
        model.setModelType(trimToNull(model.getModelType()));
        model.setProvider(trimToNull(model.getProvider()));
        model.setModelCode(trimToNull(model.getModelCode()));
        model.setBaseUrl(trimToNull(model.getBaseUrl()));
        model.setApiKey(trimToEmpty(model.getApiKey()));
        model.setRemark(trimToNull(model.getRemark()));
        model.setStatus(StringUtils.hasText(model.getStatus()) ? model.getStatus().trim() : STATUS_ENABLED);
    }

    private void fillCreateAudit(AiModelPo model) {
        model.setTenantId(resolveTenantIdForWrite());
        model.setCreateBy(resolveOperator());
        model.setCreateTime(now());
        model.setUpdateBy(resolveOperator());
        model.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiModelPo model) {
        model.setUpdateBy(resolveOperator());
        model.setUpdateTime(now());
    }

    private void enrichCredentialMetadata(List<AiModelPo> models) {
        if (models == null || models.isEmpty()) {
            return;
        }
        for (AiModelPo model : models) {
            enrichCredentialMetadata(model);
        }
    }

    private void enrichCredentialMetadata(AiModelPo model) {
        if (model == null) {
            return;
        }
        model.setCredentialConfigured(credentialResolver.isCredentialConfigured(model));
        model.setCredentialSource(credentialResolver.resolveCredentialSource(model));
    }
}
