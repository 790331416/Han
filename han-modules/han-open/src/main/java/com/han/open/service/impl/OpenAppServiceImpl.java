package com.han.open.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.HanIdUtil;
import com.han.common.core.util.PasswordUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.open.converter.OpenAppConverter;
import com.han.open.domain.dto.OpenAppDTO;
import com.han.open.domain.po.OpenAppPo;
import com.han.open.domain.po.OpenVendorPo;
import com.han.open.domain.po.OpenVendorUserPo;
import com.han.open.domain.query.OpenAppQuery;
import com.han.open.domain.vo.OpenAppVO;
import com.han.open.domain.vo.OpenAppCredentialVO;
import com.han.open.mapper.OpenAppMapper;
import com.han.open.mapper.OpenVendorMapper;
import com.han.open.mapper.OpenVendorUserMapper;
import com.han.open.service.IOpenAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 开放平台应用服务实现。
 */
@Service
public class OpenAppServiceImpl implements IOpenAppService {

    private static final int STATUS_ENABLED = 0;
    private static final int STATUS_DISABLED = 1;
    private static final int DEFAULT_ACCESS_TOKEN_TTL = 7200;
    private static final int DEFAULT_REFRESH_TOKEN_TTL = 604800;
    private static final int DEFAULT_REQUIRE_PKCE = 0;
    private static final int DEFAULT_AUTO_APPROVE = 0;
    private static final String DEFAULT_APP_TYPE = "web";
    private static final List<String> ALLOWED_APP_TYPES = List.of("web", "mobile", "server");
    private static final String DEFAULT_GRANT_TYPES = "authorization_code,refresh_token";
    private static final String DEFAULT_SCOPES = "";
    private static final String APP_KEY_PREFIX = "app_";

    public static final int LIFECYCLE_DRAFT = 0;
    public static final int LIFECYCLE_PENDING = 1;
    public static final int LIFECYCLE_SANDBOX = 2;
    public static final int LIFECYCLE_TESTING = 3;
    public static final int LIFECYCLE_PRODUCTION_PENDING = 4;
    public static final int LIFECYCLE_PRODUCTION = 5;
    public static final int LIFECYCLE_SUSPENDED = 6;
    public static final int LIFECYCLE_REVOKED = 7;
    private static final String DEFAULT_ENVIRONMENT_POLICY = "SANDBOX_FIRST";
    private static final Set<String> ADMIN_ROLES = Set.of("admin", "tenantAdmin");

    private final OpenAppMapper openAppMapper;
    private final OpenAppConverter openAppConverter;
    private final OpenVendorMapper vendorMapper;
    private final OpenVendorUserMapper vendorUserMapper;

    @Autowired
    public OpenAppServiceImpl(OpenAppMapper openAppMapper,
                              OpenAppConverter openAppConverter,
                              OpenVendorMapper vendorMapper,
                              OpenVendorUserMapper vendorUserMapper) {
        this.openAppMapper = openAppMapper;
        this.openAppConverter = openAppConverter;
        this.vendorMapper = vendorMapper;
        this.vendorUserMapper = vendorUserMapper;
    }

    /** 保留旧单元测试和旧调用方的两参数构造入口。 */
    public OpenAppServiceImpl(OpenAppMapper openAppMapper, OpenAppConverter openAppConverter) {
        this(openAppMapper, openAppConverter, null, null);
    }

    @Override
    public PageResult<OpenAppVO> selectPage(OpenAppQuery query) {
        OpenAppQuery safeQuery = query != null ? query : new OpenAppQuery();
        int pageNum = normalizePageNum(safeQuery.getPageNum());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        Page<OpenAppPo> page = openAppMapper.selectPage(new Page<>(pageNum, pageSize), buildQueryWrapper(safeQuery));
        return PageResult.of(openAppConverter.toVOList(page.getRecords()), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public OpenAppVO selectVoById(Long appId) {
        return openAppConverter.toVO(requireExisting(appId));
    }

    @Override
    public List<OpenAppDTO> selectListScope(OpenAppQuery query) {
        return selectList(query);
    }

    @Override
    public List<OpenAppDTO> selectList(OpenAppQuery query) {
        return openAppMapper.selectList(buildQueryWrapper(query)).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public OpenAppDTO selectById(Long id) {
        return toDto(requireExisting(id));
    }

    @Override
    public List<OpenAppDTO> selectByIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return List.of();
        }
        LambdaQueryWrapper<OpenAppPo> wrapper = new LambdaQueryWrapper<OpenAppPo>()
                .in(OpenAppPo::getId, ids)
                .orderByDesc(OpenAppPo::getCreateTime);
        applyOwnerScope(wrapper);
        return openAppMapper.selectList(wrapper).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(OpenAppDTO dto) {
        createWithCredentials(dto);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenAppCredentialVO createWithCredentials(OpenAppDTO dto) {
        OpenAppPo po = openAppConverter.toPo(dto);
        if (po == null) {
            throw new BusinessException("应用信息不能为空");
        }
        boolean administrator = isAdministrator();
        boolean legacyCall = SecurityContextHolder.getLoginUser() == null;
        prepareCreateOwnership(po, administrator, legacyCall);
        normalizeForCreate(po);
        if (!administrator && !legacyCall) {
            // 学校数据范围只能由平台管理员授权，厂商提交的 schoolIds 一律忽略。
            po.setSchoolScope(null);
            po.setLifecycleStatus(LIFECYCLE_DRAFT);
            po.setStatus(STATUS_ENABLED);
        } else if (po.getLifecycleStatus() == null) {
            // 旧管理员创建路径继续可直接使用 OAuth2；厂商应用必须走生命周期审批。
            po.setLifecycleStatus(LIFECYCLE_PRODUCTION);
        }
        if (!StringUtils.hasText(po.getEnvironmentPolicy())) {
            po.setEnvironmentPolicy(DEFAULT_ENVIRONMENT_POLICY);
        }
        validateForSave(po, null);
        po.setAppKey(generateAppKey());
        String appSecret = generateAppSecret();
        po.setAppSecret(PasswordUtil.encode(appSecret));
        openAppMapper.insert(po);
        // app_secret 仅为兼容旧表的内部随机值；新应用统一从分环境凭证入口获取密钥。
        return new OpenAppCredentialVO(po.getId(), po.getAppKey(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(OpenAppDTO dto) {
        Long appId = dto != null ? dto.getAppId() : null;
        if (appId == null) {
            throw new BusinessException("应用ID不能为空");
        }
        OpenAppPo existing = requireExisting(appId);
        boolean administrator = isAdministrator();
        if (!administrator && !isLegacyCall()) {
            requireVendorWriter(existing);
            if (existing.getLifecycleStatus() != null
                    && (existing.getLifecycleStatus() == LIFECYCLE_PRODUCTION
                    || existing.getLifecycleStatus() == LIFECYCLE_SUSPENDED
                    || existing.getLifecycleStatus() == LIFECYCLE_REVOKED)) {
                throw new BusinessException("当前应用状态不可编辑");
            }
        }
        Long vendorId = existing.getVendorId();
        Integer lifecycleStatus = existing.getLifecycleStatus();
        String environmentPolicy = existing.getEnvironmentPolicy();
        String schoolScope = existing.getSchoolScope();
        openAppConverter.updatePo(dto, existing);
        existing.setVendorId(vendorId);
        existing.setLifecycleStatus(lifecycleStatus);
        existing.setEnvironmentPolicy(environmentPolicy);
        if (!administrator && !isLegacyCall()) {
            // 厂商可以维护应用信息，但不能绕过管理端扩大已批准的学校范围。
            existing.setSchoolScope(schoolScope);
        }
        normalizeForUpdate(existing);
        validateForSave(existing, existing.getId());
        return openAppMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(Long id) {
        OpenAppPo existing = requireExisting(id);
        if (!isAdministrator() && !isLegacyCall()) {
            requireVendorWriter(existing);
            if (!Objects.equals(existing.getLifecycleStatus(), LIFECYCLE_DRAFT)) {
                throw new BusinessException("仅草稿应用允许删除");
            }
        }
        return openAppMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        LambdaQueryWrapper<OpenAppPo> wrapper = new LambdaQueryWrapper<OpenAppPo>()
                .in(OpenAppPo::getId, ids);
        applyOwnerScope(wrapper);
        return openAppMapper.delete(wrapper);
    }

    @Override
    public OpenAppVO getAppByAppKey(String appKey) {
        if (!StringUtils.hasText(appKey)) {
            return null;
        }
        OpenAppPo po = openAppMapper.selectOne(new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getAppKey, appKey.trim())
                .last("LIMIT 1"));
        return po != null && isProductionUsable(po) ? openAppConverter.toVO(po) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetAppSecret(Long appId) {
        requireAdministrator();
        OpenAppPo existing = requireExisting(appId);
        String newSecret = generateAppSecret();
        OpenAppPo update = new OpenAppPo();
        update.setId(existing.getId());
        update.setAppSecret(PasswordUtil.encode(newSecret));
        openAppMapper.updateById(update);
        return newSecret;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long appId, Integer status) {
        requireAdministrator();
        requireExisting(appId);
        validateStatus(status);
        OpenAppPo update = new OpenAppPo();
        update.setId(appId);
        update.setStatus(status);
        openAppMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLifecycleStatus(Long appId, Integer lifecycleStatus) {
        OpenAppPo app = requireExisting(appId);
        boolean administrator = isAdministrator();
        if (!administrator && !isLegacyCall()) {
            requireVendorWriter(app);
        } else if (!administrator && isLegacyCall()) {
            throw new BusinessException("未登录或登录已过期");
        }
        if (!isLegalLifecycleTransition(app.getLifecycleStatus(), lifecycleStatus)) {
            throw new BusinessException("应用生命周期状态转换不合法");
        }
        if (!administrator && lifecycleStatus != LIFECYCLE_PENDING
                && lifecycleStatus != LIFECYCLE_TESTING
                && lifecycleStatus != LIFECYCLE_PRODUCTION_PENDING) {
            throw new BusinessException("仅管理员可审批该应用状态");
        }
        OpenAppPo update = new OpenAppPo();
        update.setId(appId);
        update.setLifecycleStatus(lifecycleStatus);
        if (lifecycleStatus == LIFECYCLE_SUSPENDED || lifecycleStatus == LIFECYCLE_REVOKED) {
            update.setStatus(STATUS_DISABLED);
        } else if (lifecycleStatus == LIFECYCLE_PRODUCTION) {
            update.setStatus(STATUS_ENABLED);
        }
        openAppMapper.updateById(update);
    }

    @Override
    public boolean validateClient(String clientId, String clientSecret) {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            return false;
        }
        OpenAppPo po = openAppMapper.selectOne(new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getAppKey, clientId.trim())
                .eq(OpenAppPo::getStatus, STATUS_ENABLED)
                .last("LIMIT 1"));
        if (po == null || !isProductionUsable(po)) {
            return false;
        }
        if (PasswordUtil.matches(clientSecret.trim(), po.getAppSecret())) {
            return true;
        }
        // 兼容已落库的明文旧密钥：首次成功使用后升级为哈希，不影响存量第三方接入。
        if (MessageDigest.isEqual(clientSecret.trim().getBytes(StandardCharsets.UTF_8),
                po.getAppSecret().getBytes(StandardCharsets.UTF_8))) {
            OpenAppPo update = new OpenAppPo();
            update.setId(po.getId());
            update.setAppSecret(PasswordUtil.encode(clientSecret.trim()));
            openAppMapper.updateById(update);
            return true;
        }
        return false;
    }

    @Override
    public boolean validateRedirectUri(String clientId, String redirectUri) {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(redirectUri)) {
            return false;
        }
        OpenAppPo po = openAppMapper.selectOne(new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getAppKey, clientId.trim())
                .eq(OpenAppPo::getStatus, STATUS_ENABLED)
                .last("LIMIT 1"));
        if (po == null || !StringUtils.hasText(po.getRedirectUris())) {
            return false;
        }
        String target = redirectUri.trim();
        return openAppConverter.stringToList(po.getRedirectUris()).stream()
                .map(String::trim)
                .anyMatch(target::equals);
    }

    private LambdaQueryWrapper<OpenAppPo> buildQueryWrapper(OpenAppQuery query) {
        OpenAppQuery safeQuery = query != null ? query : new OpenAppQuery();
        String appType = resolveAppType(safeQuery);
        LambdaQueryWrapper<OpenAppPo> wrapper = new LambdaQueryWrapper<OpenAppPo>()
                .like(StringUtils.hasText(safeQuery.getAppName()), OpenAppPo::getAppName, safeQuery.getAppName())
                .eq(StringUtils.hasText(appType), OpenAppPo::getAppType, appType)
                .eq(safeQuery.getStatus() != null, OpenAppPo::getStatus, safeQuery.getStatus())
                .eq(resolveLifecycleStatus(safeQuery) != null, OpenAppPo::getLifecycleStatus,
                        resolveLifecycleStatus(safeQuery))
                .eq(resolveVendorId(safeQuery) != null, OpenAppPo::getVendorId, resolveVendorId(safeQuery))
                .orderByDesc(OpenAppPo::getUpdateTime)
                .orderByDesc(OpenAppPo::getCreateTime);
        applyOwnerScope(wrapper);
        return wrapper;
    }

    private String resolveAppType(OpenAppQuery query) {
        if (StringUtils.hasText(query.getAppType())) {
            return query.getAppType().trim();
        }
        if (query.getBase() != null && StringUtils.hasText(query.getBase().getAppType())) {
            return query.getBase().getAppType().trim();
        }
        return null;
    }

    private Integer resolveLifecycleStatus(OpenAppQuery query) {
        if (query.getLifecycleStatus() != null) {
            return query.getLifecycleStatus();
        }
        return query.getBase() == null ? null : query.getBase().getLifecycleStatus();
    }

    private Long resolveVendorId(OpenAppQuery query) {
        if (query.getVendorId() != null) {
            return query.getVendorId();
        }
        return query.getBase() == null ? null : query.getBase().getVendorId();
    }

    private OpenAppPo requireExisting(Long appId) {
        if (appId == null) {
            throw new BusinessException("应用ID不能为空");
        }
        OpenAppPo po = openAppMapper.selectById(appId);
        if (po == null) {
            throw new BusinessException("应用不存在");
        }
        if (!isAdministrator() && !isLegacyCall()) {
            requireVendorMember(po);
        }
        return po;
    }

    private void prepareCreateOwnership(OpenAppPo po, boolean administrator, boolean legacyCall) {
        if (administrator || legacyCall) {
            if (po.getTenantId() == null && SecurityContextHolder.getTenantId() != null) {
                po.setTenantId(SecurityContextHolder.getTenantId());
            }
            if (po.getVendorId() != null) {
                requireVendor(po.getVendorId(), administrator);
            }
            return;
        }
        Long userId = SecurityContextHolder.getUserId();
        Long tenantId = SecurityContextHolder.getTenantId();
        if (userId == null) {
            throw new BusinessException("获取当前登录用户信息失败");
        }
        if (tenantId == null) {
            throw new BusinessException("获取当前租户信息失败");
        }
        if (po.getVendorId() == null) {
            throw new BusinessException("厂商用户创建应用必须指定厂商");
        }
        OpenVendorPo vendor = requireVendor(po.getVendorId(), false);
        if (!Objects.equals(vendor.getStatus(), 4)) {
            throw new BusinessException("厂商未审核通过，不能创建应用");
        }
        requireVendorWriter(vendor.getId(), userId, tenantId);
        po.setTenantId(tenantId);
    }

    private OpenVendorPo requireVendor(Long vendorId, boolean administrator) {
        if (vendorMapper == null) {
            throw new BusinessException("厂商归属校验未配置");
        }
        OpenVendorPo vendor = vendorMapper.selectById(vendorId);
        if (vendor == null) {
            throw new BusinessException("厂商不存在");
        }
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null && !administrator) {
            throw new BusinessException("获取当前租户信息失败");
        }
        if (tenantId != null && !Objects.equals(tenantId, vendor.getTenantId())) {
            throw new BusinessException("无权操作其他租户数据");
        }
        return vendor;
    }

    private void requireVendorMember(OpenAppPo app) {
        if (app.getVendorId() == null) {
            throw new BusinessException("无权操作非厂商应用");
        }
        Long userId = SecurityContextHolder.getUserId();
        Long tenantId = SecurityContextHolder.getTenantId();
        if (userId == null) {
            throw new BusinessException("获取当前登录用户信息失败");
        }
        requireVendorWriter(app.getVendorId(), userId, tenantId, false);
    }

    private void requireVendorWriter(OpenAppPo app) {
        requireVendorWriter(app.getVendorId(), SecurityContextHolder.getUserId(),
                SecurityContextHolder.getTenantId(), true);
    }

    private void requireVendorWriter(Long vendorId, Long userId, Long tenantId) {
        requireVendorWriter(vendorId, userId, tenantId, true);
    }

    private void requireVendorWriter(Long vendorId, Long userId, Long tenantId, boolean writer) {
        if (vendorUserMapper == null || vendorId == null || userId == null || tenantId == null) {
            throw new BusinessException("厂商归属校验失败");
        }
        LambdaQueryWrapper<com.han.open.domain.po.OpenVendorUserPo> wrapper =
                new LambdaQueryWrapper<com.han.open.domain.po.OpenVendorUserPo>()
                        .eq(com.han.open.domain.po.OpenVendorUserPo::getVendorId, vendorId)
                        .eq(com.han.open.domain.po.OpenVendorUserPo::getUserId, userId)
                        .eq(com.han.open.domain.po.OpenVendorUserPo::getTenantId, tenantId)
                        .eq(com.han.open.domain.po.OpenVendorUserPo::getStatus, 0);
        if (writer) {
            wrapper.in(OpenVendorUserPo::getRole, "OWNER", "DEVELOPER");
        }
        if (vendorUserMapper.selectCount(wrapper) == 0) {
            throw new BusinessException(writer ? "当前厂商用户仅可查看应用" : "无权操作其他厂商应用");
        }
    }

    private void applyOwnerScope(LambdaQueryWrapper<OpenAppPo> wrapper) {
        if (isAdministrator() || isLegacyCall()) {
            return;
        }
        Long userId = SecurityContextHolder.getUserId();
        Long tenantId = SecurityContextHolder.getTenantId();
        if (userId == null) {
            throw new BusinessException("获取当前登录用户信息失败");
        }
        if (tenantId == null || vendorUserMapper == null) {
            throw new BusinessException("获取当前租户信息失败");
        }
        List<Long> vendorIds = vendorUserMapper.selectList(new LambdaQueryWrapper<OpenVendorUserPo>()
                        .select(OpenVendorUserPo::getVendorId)
                        .eq(com.han.open.domain.po.OpenVendorUserPo::getTenantId, tenantId)
                        .eq(com.han.open.domain.po.OpenVendorUserPo::getUserId, userId)
                        .eq(com.han.open.domain.po.OpenVendorUserPo::getStatus, 0))
                .stream().map(OpenVendorUserPo::getVendorId).distinct().toList();
        wrapper.eq(OpenAppPo::getTenantId, tenantId);
        if (vendorIds.isEmpty()) {
            wrapper.eq(OpenAppPo::getVendorId, -1L);
        } else {
            wrapper.in(OpenAppPo::getVendorId, vendorIds);
        }
    }

    private boolean isProductionUsable(OpenAppPo app) {
        return app.getVendorId() == null
                || Objects.equals(app.getLifecycleStatus(), LIFECYCLE_PRODUCTION);
    }

    private boolean isLegalLifecycleTransition(Integer current, Integer target) {
        if (current == null || target == null || Objects.equals(current, target)) {
            return false;
        }
        return switch (current) {
            case LIFECYCLE_DRAFT -> target == LIFECYCLE_PENDING;
            case LIFECYCLE_PENDING -> target == LIFECYCLE_SANDBOX;
            case LIFECYCLE_SANDBOX -> target == LIFECYCLE_TESTING;
            case LIFECYCLE_TESTING -> target == LIFECYCLE_PRODUCTION_PENDING;
            case LIFECYCLE_PRODUCTION_PENDING -> target == LIFECYCLE_PRODUCTION;
            case LIFECYCLE_PRODUCTION -> target == LIFECYCLE_SUSPENDED || target == LIFECYCLE_REVOKED;
            case LIFECYCLE_SUSPENDED -> target == LIFECYCLE_PRODUCTION || target == LIFECYCLE_REVOKED;
            default -> false;
        };
    }

    private boolean isAdministrator() {
        LoginUser user = SecurityContextHolder.getLoginUser();
        if (user == null) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        Set<String> roleKeys = user.getRoleKeys();
        return roleKeys != null && roleKeys.stream().anyMatch(ADMIN_ROLES::contains);
    }

    private void requireAdministrator() {
        if (!isAdministrator()) {
            throw new BusinessException("仅管理员可执行此操作");
        }
    }

    private boolean isLegacyCall() {
        return SecurityContextHolder.getLoginUser() == null;
    }

    private OpenAppDTO toDto(OpenAppPo po) {
        if (po == null) {
            return null;
        }
        OpenAppDTO dto = new OpenAppDTO();
        dto.setBase(po);
        dto.setRedirectUris(openAppConverter.stringToList(po.getRedirectUris()));
        dto.setScopes(openAppConverter.stringToList(po.getScopes()));
        dto.setGrantTypes(openAppConverter.stringToList(po.getGrantTypes()));
        return dto;
    }

    private void normalizeForCreate(OpenAppPo po) {
        po.setAppName(trimToNull(po.getAppName()));
        po.setAppIcon(trimToNull(po.getAppIcon()));
        po.setAppDesc(trimToNull(po.getAppDesc()));
        po.setAppType(StringUtils.hasText(po.getAppType()) ? po.getAppType().trim() : DEFAULT_APP_TYPE);
        po.setLogoutUri(trimToNull(po.getLogoutUri()));
        po.setRedirectUris(normalizeCommaSeparated(po.getRedirectUris()));
        po.setScopes(StringUtils.hasText(po.getScopes()) ? normalizeCommaSeparated(po.getScopes()) : DEFAULT_SCOPES);
        po.setSchoolScope(normalizeSchoolScope(po.getSchoolScope()));
        po.setGrantTypes(StringUtils.hasText(po.getGrantTypes()) ? normalizeCommaSeparated(po.getGrantTypes())
                : ("server".equals(po.getAppType()) ? "client_credentials" : DEFAULT_GRANT_TYPES));
        po.setAccessTokenTtl(po.getAccessTokenTtl() != null ? po.getAccessTokenTtl() : DEFAULT_ACCESS_TOKEN_TTL);
        po.setRefreshTokenTtl(po.getRefreshTokenTtl() != null ? po.getRefreshTokenTtl() : DEFAULT_REFRESH_TOKEN_TTL);
        po.setRequirePkce(po.getRequirePkce() != null ? po.getRequirePkce() : DEFAULT_REQUIRE_PKCE);
        po.setAutoApprove(po.getAutoApprove() != null ? po.getAutoApprove() : DEFAULT_AUTO_APPROVE);
        po.setStatus(po.getStatus() != null ? po.getStatus() : STATUS_ENABLED);
        po.setEnvironmentPolicy(StringUtils.hasText(po.getEnvironmentPolicy())
                ? po.getEnvironmentPolicy().trim() : DEFAULT_ENVIRONMENT_POLICY);
        po.setContactName(trimToNull(po.getContactName()));
        po.setContactPhone(trimToNull(po.getContactPhone()));
        po.setContactEmail(trimToNull(po.getContactEmail()));
        po.setRemark(trimToNull(po.getRemark()));
    }

    private void normalizeForUpdate(OpenAppPo po) {
        po.setAppName(trimToNull(po.getAppName()));
        po.setAppIcon(trimToNull(po.getAppIcon()));
        po.setAppDesc(trimToNull(po.getAppDesc()));
        po.setAppType(StringUtils.hasText(po.getAppType()) ? po.getAppType().trim() : DEFAULT_APP_TYPE);
        po.setLogoutUri(trimToNull(po.getLogoutUri()));
        po.setRedirectUris(normalizeCommaSeparated(po.getRedirectUris()));
        po.setScopes(StringUtils.hasText(po.getScopes()) ? normalizeCommaSeparated(po.getScopes()) : DEFAULT_SCOPES);
        po.setSchoolScope(normalizeSchoolScope(po.getSchoolScope()));
        po.setGrantTypes(StringUtils.hasText(po.getGrantTypes()) ? normalizeCommaSeparated(po.getGrantTypes())
                : ("server".equals(po.getAppType()) ? "client_credentials" : DEFAULT_GRANT_TYPES));
        po.setAccessTokenTtl(po.getAccessTokenTtl() != null ? po.getAccessTokenTtl() : DEFAULT_ACCESS_TOKEN_TTL);
        po.setRefreshTokenTtl(po.getRefreshTokenTtl() != null ? po.getRefreshTokenTtl() : DEFAULT_REFRESH_TOKEN_TTL);
        po.setRequirePkce(po.getRequirePkce() != null ? po.getRequirePkce() : DEFAULT_REQUIRE_PKCE);
        po.setAutoApprove(po.getAutoApprove() != null ? po.getAutoApprove() : DEFAULT_AUTO_APPROVE);
        po.setStatus(po.getStatus() != null ? po.getStatus() : STATUS_ENABLED);
        po.setEnvironmentPolicy(StringUtils.hasText(po.getEnvironmentPolicy())
                ? po.getEnvironmentPolicy().trim() : DEFAULT_ENVIRONMENT_POLICY);
        if (po.getLifecycleStatus() == null && po.getVendorId() == null) {
            po.setLifecycleStatus(LIFECYCLE_PRODUCTION);
        }
        po.setContactName(trimToNull(po.getContactName()));
        po.setContactPhone(trimToNull(po.getContactPhone()));
        po.setContactEmail(trimToNull(po.getContactEmail()));
        po.setRemark(trimToNull(po.getRemark()));
    }

    private void validateForSave(OpenAppPo po, Long currentId) {
        if (!StringUtils.hasText(po.getAppName())) {
            throw new BusinessException("应用名称不能为空");
        }
        if (!StringUtils.hasText(po.getAppType())) {
            throw new BusinessException("应用类型不能为空");
        }
        if (!ALLOWED_APP_TYPES.contains(po.getAppType())) {
            throw new BusinessException("应用类型不支持");
        }
        if (po.getAccessTokenTtl() == null || po.getAccessTokenTtl() < 60) {
            throw new BusinessException("AccessToken 有效期不能小于 60 秒");
        }
        if (po.getRefreshTokenTtl() == null || po.getRefreshTokenTtl() < 60) {
            throw new BusinessException("RefreshToken 有效期不能小于 60 秒");
        }
        if (hasEducationDirectoryScope(po.getScopes()) && !StringUtils.hasText(po.getSchoolScope())
                && !Objects.equals(po.getLifecycleStatus(), LIFECYCLE_DRAFT)) {
            throw new BusinessException("授权教师、学生或设备目录时必须指定学校范围");
        }
        validateStatus(po.getStatus());
        validateLifecycleStatus(po.getLifecycleStatus());
        if (!Set.of("SANDBOX_FIRST", "PROD_ONLY", "ALL").contains(po.getEnvironmentPolicy())) {
            throw new BusinessException("环境策略不合法");
        }
        ensureAppNameUnique(po.getAppName(), currentId);
    }

    private void ensureAppNameUnique(String appName, Long currentId) {
        LambdaQueryWrapper<OpenAppPo> wrapper = new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getAppName, appName);
        if (currentId != null) {
            wrapper.ne(OpenAppPo::getId, currentId);
        }
        if (openAppMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("应用名称已存在");
        }
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != STATUS_ENABLED && status != STATUS_DISABLED)) {
            throw new BusinessException("应用状态不合法");
        }
    }

    private void validateLifecycleStatus(Integer lifecycleStatus) {
        if (lifecycleStatus == null || lifecycleStatus < LIFECYCLE_DRAFT
                || lifecycleStatus > LIFECYCLE_REVOKED) {
            throw new BusinessException("应用生命周期状态不合法");
        }
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeCommaSeparated(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return openAppConverter.stringToList(value).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String normalizeSchoolScope(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            return openAppConverter.stringToLongList(value).stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
        } catch (NumberFormatException e) {
            throw new BusinessException("学校范围必须是有效的学校ID列表");
        }
    }

    private static boolean hasEducationDirectoryScope(String scopes) {
        return scopes != null && java.util.Arrays.stream(scopes.split(","))
                .map(String::trim)
                .anyMatch(item -> item.equals("edu.teacher.read")
                        || item.equals("edu.student.read")
                        || item.equals("edu.device.read"));
    }

    private String generateAppKey() {
        return APP_KEY_PREFIX + HanIdUtil.uuid();
    }

    private String generateAppSecret() {
        return PasswordUtil.generatePassword(20);
    }
}
