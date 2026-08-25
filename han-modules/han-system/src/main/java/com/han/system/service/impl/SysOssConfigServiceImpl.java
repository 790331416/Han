package com.han.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.AesGcmCipher;
import com.han.common.security.context.SecurityContextHolder;
import com.han.api.file.FileServiceClient;
import com.han.system.domain.po.SysOssConfigPo;
import com.han.system.domain.po.SysStorageActivePo;
import com.han.system.mapper.SysOssConfigMapper;
import com.han.system.mapper.SysStorageActiveMapper;
import com.han.system.service.ISysOssConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * OSS storage configuration service implementation.
 */
@Service
@RequiredArgsConstructor
public class SysOssConfigServiceImpl implements ISysOssConfigService {

    private static final String STATUS_NORMAL = "0";
    private static final String STATUS_DISABLED = "1";
    private static final String STATUS_READ_ONLY = "2";
    private static final String DEFAULT_REGION = "us-east-1";
    private static final String DEFAULT_HTTPS_FLAG = "1";

    private final SysOssConfigMapper ossConfigMapper;
    private final SysStorageActiveMapper storageActiveMapper;
    private final FileServiceClient fileServiceClient;

    @Value("${han.storage.master-key:}")
    private String masterKey;

    @Override
    public PageResult<SysOssConfigPo> selectPage(Integer pageNum, Integer pageSize, String configKey, String status) {
        LambdaQueryWrapper<SysOssConfigPo> wrapper = new LambdaQueryWrapper<SysOssConfigPo>()
                .like(StringUtils.hasText(configKey), SysOssConfigPo::getConfigKey, configKey)
                .eq(StringUtils.hasText(status), SysOssConfigPo::getStatus, status)
                .orderByDesc(SysOssConfigPo::getUpdateTime)
                .orderByDesc(SysOssConfigPo::getCreateTime);
        Page<SysOssConfigPo> page = ossConfigMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::mask).toList(), page.getTotal());
    }

    @Override
    public SysOssConfigPo selectById(Long ossConfigId) {
        return mask(requireExisting(ossConfigId));
    }

    @Override
    public SysOssConfigPo selectActiveConfig() {
        SysStorageActivePo active = storageActiveMapper.selectById(scopeTenantId());
        if (active == null && scopeTenantId() != 0L) {
            active = storageActiveMapper.selectById(0L);
        }
        return active == null ? null : mask(requireExisting(active.getOssConfigId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(SysOssConfigPo config) {
        validateConfig(config, null);
        normalizeConfig(config);
        populateCreateAudit(config);
        ensureConfigKeyUnique(config.getConfigKey(), config.getTenantId(), null);
        ossConfigMapper.insert(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysOssConfigPo config) {
        if (config == null || config.getOssConfigId() == null) {
            throw new BusinessException("OSS配置ID不能为空");
        }
        SysOssConfigPo existing = requireExisting(config.getOssConfigId());

        validateConfig(config, existing.getOssConfigId());
        ensureConfigKeyUnique(config.getConfigKey(), existing.getTenantId(), existing.getOssConfigId());

        SysOssConfigPo toUpdate = new SysOssConfigPo();
        toUpdate.setOssConfigId(existing.getOssConfigId());
        toUpdate.setConfigKey(config.getConfigKey());
        toUpdate.setAccessKeyCiphertext(StringUtils.hasText(config.getAccessKey())
                ? encrypt(config.getAccessKey()) : existing.getAccessKeyCiphertext());
        toUpdate.setSecretKeyCiphertext(StringUtils.hasText(config.getSecretKey())
                ? encrypt(config.getSecretKey()) : existing.getSecretKeyCiphertext());
        toUpdate.setKeyVersion(1);
        toUpdate.setConfigName(config.getConfigName());
        toUpdate.setProviderType(config.getProviderType());
        toUpdate.setBucketName(config.getBucketName());
        toUpdate.setPrefix(config.getPrefix());
        toUpdate.setEndpoint(config.getEndpoint());
        toUpdate.setPublicEndpoint(config.getPublicEndpoint());
        toUpdate.setRegion(config.getRegion());
        toUpdate.setIsHttps(StringUtils.hasText(config.getIsHttps()) ? config.getIsHttps() : existing.getIsHttps());
        toUpdate.setPathStyle(config.getPathStyle() != null ? config.getPathStyle() : existing.getPathStyle());
        toUpdate.setConfigVersion(existing.getConfigVersion() == null ? 1 : existing.getConfigVersion() + 1);
        toUpdate.setStatus(StringUtils.hasText(config.getStatus()) ? config.getStatus() : existing.getStatus());
        toUpdate.setRemark(config.getRemark());
        toUpdate.setTenantId(existing.getTenantId());

        normalizeConfig(toUpdate);
        populateUpdateAudit(toUpdate);
        ossConfigMapper.updateById(toUpdate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long ossConfigId) {
        requireExisting(ossConfigId);
        if (storageActiveMapper.selectList(new LambdaQueryWrapper<SysStorageActivePo>()
                .eq(SysStorageActivePo::getOssConfigId, ossConfigId)).size() > 0) {
            throw new BusinessException("当前默认写入存储不能删除，请先切换到其他配置");
        }
        ossConfigMapper.deleteById(ossConfigId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long ossConfigId) {
        SysOssConfigPo existing = requireExisting(ossConfigId);

        if (!STATUS_NORMAL.equals(existing.getStatus())) {
            throw new BusinessException("只有正常状态的OSS配置可以设为默认写入存储");
        }
        testConnection(ossConfigId);
        Long tenantId = storageTenantId(existing.getTenantId());
        SysStorageActivePo active = storageActiveMapper.selectById(tenantId);
        if (active == null) {
            active = new SysStorageActivePo();
            active.setTenantId(tenantId);
            active.setOssConfigId(ossConfigId);
            active.setVersion(1);
            active.setUpdateBy(SecurityContextHolder.getUserId());
            active.setUpdateTime(LocalDateTime.now());
            storageActiveMapper.insert(active);
            return;
        }
        active.setOssConfigId(ossConfigId);
        active.setVersion(active.getVersion() == null ? 1 : active.getVersion() + 1);
        active.setUpdateBy(SecurityContextHolder.getUserId());
        active.setUpdateTime(LocalDateTime.now());
        storageActiveMapper.updateById(active);
    }

    @Override
    public void testConnection(Long ossConfigId) {
        requireExisting(ossConfigId);
        var result = fileServiceClient.testStorage(ossConfigId);
        if (result == null || !result.isSuccess()) {
            throw new BusinessException(result == null || !StringUtils.hasText(result.getMsg())
                    ? "对象存储连接测试失败" : result.getMsg());
        }
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
        if (currentId == null && !StringUtils.hasText(config.getAccessKey())) {
            throw new BusinessException("AccessKey不能为空");
        }
        if (currentId == null && !StringUtils.hasText(config.getSecretKey())) {
            throw new BusinessException("SecretKey不能为空");
        }
        if (!StringUtils.hasText(config.getBucketName())) {
            throw new BusinessException("桶名称不能为空");
        }
        if (currentId != null && !currentId.equals(config.getOssConfigId())) {
            throw new BusinessException("OSS配置ID不匹配");
        }
    }

    private void ensureConfigKeyUnique(String configKey, Long tenantId, Long excludeId) {
        LambdaQueryWrapper<SysOssConfigPo> wrapper = new LambdaQueryWrapper<SysOssConfigPo>()
                .eq(SysOssConfigPo::getConfigKey, configKey)
                .eq(SysOssConfigPo::getTenantId, storageTenantId(tenantId));
        if (excludeId != null) {
            wrapper.ne(SysOssConfigPo::getOssConfigId, excludeId);
        }
        if (ossConfigMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("配置Key[" + configKey + "]已存在");
        }
    }

    private void normalizeConfig(SysOssConfigPo config) {
        config.setConfigKey(trimToNull(config.getConfigKey()));
        config.setConfigName(trimToNull(config.getConfigName()));
        config.setProviderType(StringUtils.hasText(config.getProviderType()) ? config.getProviderType().trim() : "S3");
        if (StringUtils.hasText(config.getAccessKey())) {
            config.setAccessKeyCiphertext(encrypt(config.getAccessKey()));
        }
        if (StringUtils.hasText(config.getSecretKey())) {
            config.setSecretKeyCiphertext(encrypt(config.getSecretKey()));
        }
        config.setAccessKey(null);
        config.setSecretKey(null);
        config.setKeyVersion(1);
        config.setBucketName(trimToNull(config.getBucketName()));
        config.setEndpoint(trimToNull(config.getEndpoint()));
        config.setPublicEndpoint(trimToNull(config.getPublicEndpoint()));
        config.setRegion(StringUtils.hasText(config.getRegion()) ? config.getRegion().trim() : DEFAULT_REGION);
        config.setPrefix(StringUtils.hasText(config.getPrefix()) ? config.getPrefix().trim() : "");
        config.setRemark(StringUtils.hasText(config.getRemark()) ? config.getRemark().trim() : null);
        config.setIsHttps(StringUtils.hasText(config.getIsHttps()) ? config.getIsHttps().trim() : DEFAULT_HTTPS_FLAG);
        config.setPathStyle(config.getPathStyle() == null || config.getPathStyle());
        config.setConfigVersion(config.getConfigVersion() == null ? 1 : config.getConfigVersion());
        config.setStatus(StringUtils.hasText(config.getStatus()) ? config.getStatus().trim() : STATUS_DISABLED);
    }

    private void populateCreateAudit(SysOssConfigPo config) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = SecurityContextHolder.getUserId();
        Long tenantId = config.getTenantId() != null ? config.getTenantId() : SecurityContextHolder.getTenantId();
        config.setTenantId(storageTenantId(tenantId));
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

    private Long scopeTenantId() {
        return storageTenantId(SecurityContextHolder.getTenantId());
    }

    private Long storageTenantId(Long tenantId) {
        return tenantId == null ? 0L : tenantId;
    }

    private String encrypt(String value) {
        return AesGcmCipher.encrypt(masterKey, value.trim());
    }

    private SysOssConfigPo mask(SysOssConfigPo config) {
        if (config == null) {
            return null;
        }
        config.setAccessKey(maskAccessKey(config.getAccessKeyCiphertext()));
        config.setSecretKey("********");
        config.setAccessKeyCiphertext(null);
        config.setSecretKeyCiphertext(null);
        return config;
    }

    private String maskAccessKey(String ciphertext) {
        if (!StringUtils.hasText(ciphertext)) {
            return "";
        }
        return "****";
    }
}
