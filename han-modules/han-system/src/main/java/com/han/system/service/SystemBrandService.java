package com.han.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.exception.BusinessException;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.common.security.context.SecurityContextHolder;
import com.han.system.domain.dto.SystemBrandDto;
import com.han.system.domain.po.SysConfigPo;
import com.han.system.domain.vo.SystemBrandVo;
import com.han.system.mapper.SysConfigMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Set;

/**
 * 平台统一品牌配置。
 *
 * <p>品牌不属于任何租户：固定写入 tenant_id=0，并在查询时显式忽略租户条件。
 * 这样登录前读取不会因没有租户上下文而读到其他租户的同名参数。</p>
 */
@Service
public class SystemBrandService {

    public static final String DISPLAY_FULL_NAME = "FULL_NAME";
    public static final String DISPLAY_SHORT_NAME = "SHORT_NAME";

    private static final long PLATFORM_TENANT_ID = 0L;
    private static final String KEY_FULL_NAME = "sys.brand.fullName";
    private static final String KEY_SHORT_NAME = "sys.brand.shortName";
    private static final String KEY_DISPLAY_MODE = "sys.brand.displayMode";
    private static final String KEY_LOGIN_SUBTITLE = "sys.brand.loginSubtitle";
    private static final Set<String> RESERVED_KEYS = Set.of(
            KEY_FULL_NAME, KEY_SHORT_NAME, KEY_DISPLAY_MODE, KEY_LOGIN_SUBTITLE);

    private static final String DEFAULT_FULL_NAME = "HAN Cloud";
    private static final String DEFAULT_SHORT_NAME = "HAN";
    private static final String DEFAULT_LOGIN_SUBTITLE = "企业级多租户微服务平台";
    private static final String PUBLIC_LOGO_URL = "/system/public/brand/logo";
    private static final long MAX_LOGO_SIZE = 1024 * 1024;

    private final SysConfigMapper configMapper;
    private final Path logoFile;

    public SystemBrandService(SysConfigMapper configMapper,
                              @Value("${han.brand.logo-path:/data/brand}") String logoPath) {
        this.configMapper = configMapper;
        this.logoFile = Path.of(logoPath).resolve("logo.bin");
    }

    /** 登录前与登录后共用的只读品牌信息；永远不会返回任意系统参数。 */
    public SystemBrandVo getBrand() {
        return TenantHelper.ignore(() -> toView(
                valueOf(KEY_FULL_NAME, DEFAULT_FULL_NAME),
                valueOf(KEY_SHORT_NAME, DEFAULT_SHORT_NAME),
                valueOf(KEY_DISPLAY_MODE, DISPLAY_FULL_NAME),
                valueOf(KEY_LOGIN_SUBTITLE, DEFAULT_LOGIN_SUBTITLE)
        ));
    }

    /** 四项品牌字段必须作为一个事务一起保存，避免前端读到半套配置。 */
    @Transactional(rollbackFor = Exception.class)
    public SystemBrandVo updateBrand(SystemBrandDto form) {
        SystemBrandVo brand = toView(
                requiredText(form.getFullName(), "系统全称", 64),
                requiredText(form.getShortName(), "系统简称", 32),
                displayMode(form.getDisplayMode()),
                optionalText(form.getLoginSubtitle(), "登录页副标题", 128)
        );
        TenantHelper.ignore(() -> {
            upsert(KEY_FULL_NAME, "系统品牌全称", brand.fullName());
            upsert(KEY_SHORT_NAME, "系统品牌简称", brand.shortName());
            upsert(KEY_DISPLAY_MODE, "系统品牌统一展示方式", brand.displayMode());
            upsert(KEY_LOGIN_SUBTITLE, "登录页副标题", brand.loginSubtitle());
        });
        return brand;
    }

    /** 防止只有参数配置权限的用户绕过系统设置菜单权限篡改品牌。 */
    public void assertGenericConfigMutationAllowed(Long configId, String requestedConfigKey) {
        boolean reserved = isReservedConfigKey(requestedConfigKey);
        if (configId != null) {
            reserved = reserved || TenantHelper.ignore(() -> {
                SysConfigPo existing = configMapper.selectById(configId);
                return existing != null && isReservedConfigKey(existing.getConfigKey());
            });
        }
        if (reserved && !canEditBrand()) {
            throw new BusinessException("当前用户没有修改系统设置权限");
        }
    }

    public void updateLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择Logo文件");
        }
        if (file.getSize() > MAX_LOGO_SIZE) {
            throw new BusinessException("Logo文件不能超过1MB");
        }
        try {
            byte[] content = file.getBytes();
            if (content.length > MAX_LOGO_SIZE) {
                throw new BusinessException("Logo文件不能超过1MB");
            }
            if (contentType(content) == null) {
                throw new BusinessException("Logo仅支持PNG、JPG或WebP格式");
            }
            Files.createDirectories(logoFile.getParent());
            Path temporary = Files.createTempFile(logoFile.getParent(), "logo-", ".tmp");
            try {
                Files.write(temporary, content, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                moveReplacing(temporary, logoFile);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("Logo保存失败，请稍后重试");
        }
    }

    public Optional<BrandLogo> getLogo() {
        if (!Files.isRegularFile(logoFile)) {
            return Optional.empty();
        }
        try {
            byte[] content = Files.readAllBytes(logoFile);
            String contentType = contentType(content);
            return contentType == null ? Optional.empty() : Optional.of(new BrandLogo(content, contentType));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static boolean isReservedConfigKey(String configKey) {
        return configKey != null && RESERVED_KEYS.contains(configKey.trim());
    }

    private String valueOf(String key, String fallback) {
        SysConfigPo config = configMapper.selectOne(new LambdaQueryWrapper<SysConfigPo>()
                .eq(SysConfigPo::getTenantId, PLATFORM_TENANT_ID)
                .eq(SysConfigPo::getConfigKey, key)
                .orderByDesc(SysConfigPo::getId)
                .last("LIMIT 1"));
        return config == null ? fallback : config.getConfigValue();
    }

    private void upsert(String key, String name, String value) {
        SysConfigPo config = configMapper.selectOne(new LambdaQueryWrapper<SysConfigPo>()
                .eq(SysConfigPo::getTenantId, PLATFORM_TENANT_ID)
                .eq(SysConfigPo::getConfigKey, key)
                .orderByDesc(SysConfigPo::getId)
                .last("LIMIT 1"));
        if (config == null) {
            config = new SysConfigPo();
            config.setTenantId(PLATFORM_TENANT_ID);
            config.setConfigName(name);
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setConfigType("Y");
            config.setRemark("平台全局品牌配置，仅超级管理员可维护");
            configMapper.insert(config);
            return;
        }
        config.setConfigValue(value);
        configMapper.updateById(config);
    }

    private SystemBrandVo toView(String fullName, String shortName, String displayMode, String subtitle) {
        String normalizedFullName = safeReadText(fullName, DEFAULT_FULL_NAME, 64);
        String normalizedShortName = safeReadText(shortName, DEFAULT_SHORT_NAME, 32);
        String normalizedMode = DISPLAY_SHORT_NAME.equals(displayMode) ? DISPLAY_SHORT_NAME : DISPLAY_FULL_NAME;
        String displayName = DISPLAY_SHORT_NAME.equals(normalizedMode) ? normalizedShortName : normalizedFullName;
        return new SystemBrandVo(
                normalizedFullName,
                normalizedShortName,
                normalizedMode,
                displayName,
                safeOptionalReadText(subtitle, 128),
                Files.isRegularFile(logoFile) ? PUBLIC_LOGO_URL : ""
        );
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        String normalized = optionalText(value, fieldName, maxLength);
        if (normalized.isEmpty()) {
            throw new BusinessException(fieldName + "不能为空");
        }
        return normalized;
    }

    private String optionalText(String value, String fieldName, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(fieldName + "不能超过" + maxLength + "个字符");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(fieldName + "不能包含控制字符");
        }
        return normalized;
    }

    private String displayMode(String value) {
        if (!DISPLAY_FULL_NAME.equals(value) && !DISPLAY_SHORT_NAME.equals(value)) {
            throw new BusinessException("统一展示名称取值无效");
        }
        return value;
    }

    private String safeReadText(String value, String fallback, int maxLength) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            return fallback;
        }
        return normalized;
    }

    private String safeOptionalReadText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength || normalized.chars().anyMatch(Character::isISOControl)) {
            return "";
        }
        return normalized;
    }

    private boolean canEditBrand() {
        return SecurityContextHolder.getLoginUser() != null
                && SecurityContextHolder.getLoginUser().hasPermission("system:brand:edit");
    }

    private String contentType(byte[] content) {
        if (content.length >= 8
                && (content[0] & 0xff) == 0x89 && content[1] == 0x50 && content[2] == 0x4e && content[3] == 0x47
                && content[4] == 0x0d && content[5] == 0x0a && content[6] == 0x1a && content[7] == 0x0a) {
            return "image/png";
        }
        if (content.length >= 3 && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8
                && (content[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (content.length >= 12 && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record BrandLogo(byte[] content, String contentType) {
    }
}
