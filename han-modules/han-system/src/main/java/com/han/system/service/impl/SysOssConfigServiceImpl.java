package com.han.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.system.domain.po.SysOssConfigPo;
import com.han.system.mapper.SysOssConfigMapper;
import com.han.system.service.ISysOssConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 对象存储配置服务实现。
 */
@Service
@RequiredArgsConstructor
public class SysOssConfigServiceImpl implements ISysOssConfigService {

    private static final String STATUS_ENABLED = "0";
    private static final String STATUS_DISABLED = "1";
    private static final String DEFAULT_REGION = "us-east-1";
    private static final String DEFAULT_HTTPS_FLAG = "1";

    private final SysOssConfigMapper ossConfigMapper;

    @Override
    public PageResult<SysOssConfigPo> selectPage(Integer pageNum, Integer pageSize, String configKey, String status) {
        LambdaQueryWrapper<SysOssConfigPo> wrapper = new LambdaQueryWrapper<SysOssConfigPo>()
                .like(StringUtils.hasText(configKey), SysOssConfigPo::getConfigKey, configKey)
                .eq(StringUtils.hasText(status), SysOssConfigPo::getStatus, status)
                .orderByDesc(SysOssConfigPo::getUpdateTime)
                .orderByDesc(SysOssConfigPo::getCreateTime);
        Page<SysOssConfigPo> page = ossConfigMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    public SysOssConfigPo selectById(Long ossConfigId) {
        return requireExisting(ossConfigId);
    }

    @Override
    public SysOssConfigPo selectActiveConfig() {
        return ossConfigMapper.selectOne(new LambdaQueryWrapper<SysOssConfigPo>()
                .eq(SysOssConfigPo::getStatus, STATUS_ENABLED)
                .orderByDesc(SysOssConfigPo::getUpdateTime)
                .orderByDesc(SysOssConfigPo::getCreateTime)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(SysOssConfigPo config) {
        validateConfig(config, null);
        ensureConfigKeyUnique(config.getConfigKey(), null);

        normalizeConfig(config);
        populateCreateAudit(config);
        ossConfigMapper.insert(config);

        if (STATUS_ENABLED.equals(config.getStatus())) {
            changeStatus(config.getOssConfigId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysOssConfigPo config) {
        if (config == null || config.getOssConfigId() == null) {
            throw new BusinessException("OSS配置ID不能为空");
        }
        SysOssConfigPo existing = requireExisting(config.getOssConfigId());

        validateConfig(config, existing.getOssConfigId());
        ensureConfigKeyUnique(config.getConfigKey(), existing.getOssConfigId());

        SysOssConfigPo toUpdate = new SysOssConfigPo();
        toUpdate.setOssConfigId(existing.getOssConfigId());
        toUpdate.setConfigKey(config.getConfigKey());
        toUpdate.setAccessKey(config.getAccessKey());
        toUpdate.setSecretKey(config.getSecretKey());
        toUpdate.setBucketName(config.getBucketName());
        toUpdate.setPrefix(config.getPrefix());
        toUpdate.setEndpoint(config.getEndpoint());
        toUpdate.setRegion(config.getRegion());
        toUpdate.setIsHttps(StringUtils.hasText(config.getIsHttps()) ? config.getIsHttps() : existing.getIsHttps());
        toUpdate.setStatus(StringUtils.hasText(config.getStatus()) ? config.getStatus() : existing.getStatus());
        toUpdate.setRemark(config.getRemark());
        toUpdate.setTenantId(existing.getTenantId());

        normalizeConfig(toUpdate);
        populateUpdateAudit(toUpdate);
        ossConfigMapper.updateById(toUpdate);

        if (STATUS_ENABLED.equals(toUpdate.getStatus())) {
            changeStatus(toUpdate.getOssConfigId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long ossConfigId) {
        requireExisting(ossConfigId);
        ossConfigMapper.deleteById(ossConfigId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long ossConfigId) {
        SysOssConfigPo existing = requireExisting(ossConfigId);

        LambdaUpdateWrapper<SysOssConfigPo> disableWrapper = new LambdaUpdateWrapper<>();
        if (existing.getTenantId() != null) {
            disableWrapper.eq(SysOssConfigPo::getTenantId, existing.getTenantId());
        } else {
            disableWrapper.isNull(SysOssConfigPo::getTenantId);
        }
        disableWrapper.set(SysOssConfigPo::getStatus, STATUS_DISABLED)
                .set(SysOssConfigPo::getUpdateBy, SecurityContextHolder.getUserId())
                .set(SysOssConfigPo::getUpdateTime, LocalDateTime.now());
        ossConfigMapper.update(null, disableWrapper);

        SysOssConfigPo enabled = new SysOssConfigPo();
        enabled.setOssConfigId(ossConfigId);
        enabled.setStatus(STATUS_ENABLED);
        populateUpdateAudit(enabled);
        ossConfigMapper.updateById(enabled);
    }

    private SysOssConfigPo requireExisting(Long ossConfigId) {
        SysOssConfigPo config = ossConfigMapper.selectById(ossConfigId);
        if (config == null) {
            throw new BusinessException("OSS配置不存在");
        }
        return config;
    }

    private void validateConfig(SysOssConfigPo config, Long currentId) {
        if (config == null) {
            throw new BusinessException("OSS配置不能为空");
        }
        if (!StringUtils.hasText(config.getConfigKey())) {
            throw new BusinessException("配置Key不能为空");
        }
        if (!StringUtils.hasText(config.getEndpoint())) {
            throw new BusinessException("访问端点不能为空");
        }
        if (!StringUtils.hasText(config.getAccessKey())) {
            throw new BusinessException("AccessKey不能为空");
        }
        if (!StringUtils.hasText(config.getSecretKey())) {
            throw new BusinessException("SecretKey不能为空");
        }
        if (!StringUtils.hasText(config.getBucketName())) {
            throw new BusinessException("桶名称不能为空");
        }
        if (currentId != null && !currentId.equals(config.getOssConfigId())) {
            throw new BusinessException("OSS配置ID不匹配");
        }
    }

    private void ensureConfigKeyUnique(String configKey, Long excludeId) {
        LambdaQueryWrapper<SysOssConfigPo> wrapper = new LambdaQueryWrapper<SysOssConfigPo>()
                .eq(SysOssConfigPo::getConfigKey, configKey);
        if (excludeId != null) {
            wrapper.ne(SysOssConfigPo::getOssConfigId, excludeId);
        }
        if (ossConfigMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("配置Key[" + configKey + "]已存在");
        }
    }

    private void normalizeConfig(SysOssConfigPo config) {
        config.setConfigKey(trimToNull(config.getConfigKey()));
        config.setAccessKey(trimToNull(config.getAccessKey()));
        config.setSecretKey(trimToNull(config.getSecretKey()));
        config.setBucketName(trimToNull(config.getBucketName()));
        config.setEndpoint(trimToNull(config.getEndpoint()));
        config.setRegion(StringUtils.hasText(config.getRegion()) ? config.getRegion().trim() : DEFAULT_REGION);
        config.setPrefix(StringUtils.hasText(config.getPrefix()) ? config.getPrefix().trim() : "");
        config.setRemark(StringUtils.hasText(config.getRemark()) ? config.getRemark().trim() : null);
        config.setIsHttps(StringUtils.hasText(config.getIsHttps()) ? config.getIsHttps().trim() : DEFAULT_HTTPS_FLAG);
        config.setStatus(StringUtils.hasText(config.getStatus()) ? config.getStatus().trim() : STATUS_DISABLED);
    }

    private void populateCreateAudit(SysOssConfigPo config) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = SecurityContextHolder.getUserId();
        Long tenantId = SecurityContextHolder.getTenantId();
        config.setTenantId(config.getTenantId() != null ? config.getTenantId() : tenantId);
        config.setCreateBy(userId);
        config.setCreateTime(now);
        config.setUpdateBy(userId);
        config.setUpdateTime(now);
    }

    private void populateUpdateAudit(SysOssConfigPo config) {
        config.setUpdateBy(SecurityContextHolder.getUserId());
        config.setUpdateTime(LocalDateTime.now());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
