package com.han.open.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.domain.PageResult;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.open.converter.OpenAppAuthorizationConverter;
import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.domain.po.OpenAppCredentialPo;
import com.han.open.domain.po.OpenAppPo;
import com.han.open.domain.po.OpenAppResourceGrantPo;
import com.han.open.domain.po.OpenAuthorizationRequestPo;
import com.han.open.domain.po.OpenVendorPo;
import com.han.open.domain.po.OpenVendorUserPo;
import com.han.open.domain.vo.AppCredentialVO;
import com.han.open.domain.vo.AppGrantDetailVO;
import com.han.open.domain.vo.GrantApplyVO;
import com.han.open.domain.vo.OpenAppCredentialAdminVO;
import com.han.open.domain.vo.OpenAuthorizationRequestAdminVO;
import com.han.open.mapper.OpenAppCredentialMapper;
import com.han.open.mapper.OpenApiResourceMapper;
import com.han.open.mapper.OpenAppMapper;
import com.han.open.mapper.OpenAppResourceGrantMapper;
import com.han.open.mapper.OpenAuthorizationRequestMapper;
import com.han.open.mapper.OpenVendorUserMapper;
import com.han.open.mapper.OpenVendorMapper;
import com.han.open.service.OpenAppAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

/**
 * 应用授权服务实现
 */
@Service
@RequiredArgsConstructor
public class OpenAppAuthorizationServiceImpl extends ServiceImpl<OpenAppResourceGrantMapper, OpenAppResourceGrantPo> implements OpenAppAuthorizationService {

    private static final Set<String> ADMIN_ROLES = Set.of("admin", "tenantAdmin");
    private static final Set<String> ALLOWED_ENVIRONMENTS = Set.of("SANDBOX", "PROD");
    private static final int REQUEST_PENDING = 0;
    private static final int REQUEST_APPROVED = 1;
    private static final int REQUEST_REJECTED = 2;
    private static final int REQUEST_SANDBOX_OPEN = 3;
    private static final int REQUEST_PRODUCTION_OPEN = 4;
    private static final int GRANT_PENDING = 0;
    private static final int GRANT_ACTIVE = 1;
    private static final int GRANT_REJECTED = 2;
    private static final int GRANT_REVOKED = 4;
    private static final int APP_STATUS_ENABLED = 0;
    private static final int LIFECYCLE_SANDBOX = 2;
    private static final int LIFECYCLE_DRAFT = 0;
    private static final int LIFECYCLE_PENDING = 1;
    private static final int LIFECYCLE_TESTING = 3;
    private static final int LIFECYCLE_PRODUCTION_PENDING = 4;
    private static final int LIFECYCLE_PRODUCTION = 5;
    private static final int LIFECYCLE_SUSPENDED = 6;
    private static final int LIFECYCLE_REVOKED = 7;
    private static final int VENDOR_STATUS_APPROVED = 4;

    private final OpenAuthorizationRequestMapper authorizationRequestMapper;
    private final OpenAppCredentialMapper appCredentialMapper;
    private final ObjectMapper objectMapper;
    private final OpenAppMapper appMapper;
    private final OpenVendorUserMapper vendorUserMapper;
    private final OpenApiResourceMapper resourceMapper;
    private final OpenVendorMapper vendorMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public PageResult<OpenAuthorizationRequestAdminVO> listRequestPage(
            Long appId, Integer status, String environment, Integer pageNum, Integer pageSize) {
        Long tenantId = requireTenantId();
        int safePageNum = normalizePageNum(pageNum);
        int safePageSize = normalizePageSize(pageSize);
        LambdaQueryWrapper<OpenAuthorizationRequestPo> wrapper = new LambdaQueryWrapper<OpenAuthorizationRequestPo>()
                .eq(OpenAuthorizationRequestPo::getTenantId, tenantId)
                .eq(OpenAuthorizationRequestPo::getDelFlag, 0)
                .le(OpenAuthorizationRequestPo::getRequestType, 2)
                .orderByDesc(OpenAuthorizationRequestPo::getCreateTime);
        applyReadableAppScope(wrapper, OpenAuthorizationRequestPo::getAppId,
                tenantId, SecurityContextHolder.getUserId());
        if (appId != null) {
            wrapper.eq(OpenAuthorizationRequestPo::getAppId, appId);
        }
        if (status != null) {
            wrapper.eq(OpenAuthorizationRequestPo::getStatus, status);
        }
        if (StringUtils.hasText(environment)) {
            wrapper.eq(OpenAuthorizationRequestPo::getEnvironment, normalizeEnvironment(environment));
        }
        Page<OpenAuthorizationRequestPo> page = authorizationRequestMapper.selectPage(
                new Page<>(safePageNum, safePageSize), wrapper);
        List<OpenAuthorizationRequestAdminVO> rows = page.getRecords().stream()
                .map(OpenAppAuthorizationConverter::toRequestAdminVO).toList();
        return PageResult.of(rows, page.getTotal(), safePageNum, safePageSize);
    }

    @Override
    public List<OpenAppCredentialAdminVO> listCredentials(Long appId) {
        Long tenantId = requireTenantId();
        LambdaQueryWrapper<OpenAppCredentialPo> wrapper = new LambdaQueryWrapper<OpenAppCredentialPo>()
                        .eq(OpenAppCredentialPo::getTenantId, tenantId)
                        .eq(OpenAppCredentialPo::getDelFlag, 0)
                        .orderByAsc(OpenAppCredentialPo::getEnvironment)
                        .orderByDesc(OpenAppCredentialPo::getCreateTime);
        applyReadableAppScope(wrapper, OpenAppCredentialPo::getAppId,
                tenantId, SecurityContextHolder.getUserId());
        if (appId != null) {
            wrapper.eq(OpenAppCredentialPo::getAppId, appId);
        }
        return appCredentialMapper.selectList(wrapper).stream()
                .map(OpenAppAuthorizationConverter::toCredentialAdminVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitGrantApply(GrantApplyVO applyVO) {
        Long currentUserId = SecurityContextHolder.getUserId();
        if (currentUserId == null) {
            throw new BusinessException("获取当前登录用户信息失败");
        }
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("获取当前租户信息失败");
        }
        if (applyVO == null) {
            throw new BusinessException("授权申请不能为空");
        }

        String environment = normalizeEnvironment(applyVO.getEnvironment());
        applyVO.setEnvironment(environment);
        OpenAppPo app = requireOwnedApp(applyVO.getAppId(), tenantId, currentUserId);
        if (applyVO.getResources() == null || applyVO.getResources().isEmpty()) {
            throw new BusinessException("申请的资源列表不能为空");
        }

        Set<Long> resourceIds = new HashSet<>();
        List<Long> grantIds = new java.util.ArrayList<>();
        for (GrantApplyVO.ResourceApplyItem item : applyVO.getResources()) {
            if (item == null || item.getResourceId() == null || !resourceIds.add(item.getResourceId())) {
                throw new BusinessException("申请资源不能为空且不能重复");
            }
            OpenApiResourcePo resource = requireAppableResource(item.getResourceId());
            item.setScopes(resolveRequestedScope(item.getScopes(), resource.getScopeCode()));
            validateResourceItem(item, app);

            OpenAppResourceGrantPo exist = findGrant(tenantId, applyVO.getAppId(), item.getResourceId(), environment);
            if (exist != null && Integer.valueOf(GRANT_PENDING).equals(exist.getStatus())) {
                throw new BusinessException("资源ID:" + item.getResourceId() + "已有待审核的授权申请，请勿重复提交");
            }
            if (exist != null && Integer.valueOf(GRANT_ACTIVE).equals(exist.getStatus()) && !isExpired(exist.getExpiresAt())) {
                throw new BusinessException("资源ID:" + item.getResourceId() + "已有生效授权，请勿重复申请");
            }

            OpenAppResourceGrantPo grant = exist != null ? exist : new OpenAppResourceGrantPo();
            applyPendingGrant(grant, applyVO, item, tenantId, currentUserId);
            if (exist == null) {
                baseMapper.insert(grant);
            } else {
                baseMapper.updateById(grant);
            }
            if (grant.getId() != null) {
                grantIds.add(grant.getId());
            }
        }

        // 创建授权申请记录
        OpenAuthorizationRequestPo request = new OpenAuthorizationRequestPo();
        request.setTenantId(tenantId);
        request.setAppId(applyVO.getAppId());
        request.setEnvironment(environment);
        request.setGrantId(grantIds.size() == 1 ? grantIds.get(0) : null);
        request.setRequestType(0); // 新增授权
        request.setStatus(REQUEST_PENDING); // 待审核
        try {
            request.setRequestData(objectMapper.writeValueAsString(applyVO.getResources()));
        } catch (JsonProcessingException e) {
            throw new BusinessException("500", "序列化资源申请数据失败", e);
        }
        request.setReason(applyVO.getApplyReason());
        request.setApplicantId(currentUserId);
        request.setCreateBy(currentUserId);
        authorizationRequestMapper.insert(request);

        return request.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reviewGrantApply(Long requestId, Integer status, String reason) {
        validateReviewStatus(status);
        OpenAuthorizationRequestPo request = authorizationRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException("申请记录不存在");
        }
        if (!Integer.valueOf(REQUEST_PENDING).equals(request.getStatus())) { // 只能审核待审核的申请
            throw new BusinessException("申请状态不正确，无法审核");
        }

        Long currentUserId = SecurityContextHolder.getUserId();
        if (currentUserId == null) {
            throw new BusinessException("获取当前登录用户信息失败");
        }
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("获取当前租户信息失败");
        }
        if (request.getTenantId() != null && !tenantId.equals(request.getTenantId())) {
            throw new BusinessException("无权审核其他租户的授权申请");
        }
        requireAdministrator();
        OpenAppPo app = requireOwnedApp(request.getAppId(), tenantId, currentUserId);

        // 条件更新确保同一申请只能被一个审核请求成功领取。
        OpenAuthorizationRequestPo update = new OpenAuthorizationRequestPo();
        update.setStatus(status);
        update.setReviewReason(reason);
        update.setReviewerId(currentUserId);
        update.setReviewTime(LocalDateTime.now());
        update.setUpdateBy(currentUserId);
        int claimed = authorizationRequestMapper.update(update, new UpdateWrapper<OpenAuthorizationRequestPo>()
                .eq("id", requestId)
                .eq("tenant_id", tenantId)
                .eq("status", REQUEST_PENDING));
        if (claimed != 1) {
            throw new BusinessException("申请已被其他管理员审核，请刷新后重试");
        }
        request.setStatus(status);
        request.setReviewReason(reason);
        request.setReviewerId(currentUserId);
        request.setReviewTime(update.getReviewTime());

        List<GrantApplyVO.ResourceApplyItem> resources = readRequestResources(request);
        if (status == REQUEST_APPROVED && resources.isEmpty()) {
            throw new BusinessException("授权申请资源数据为空");
        }
        String environment = resources.isEmpty() ? null : normalizeEnvironment(request.getEnvironment());
        for (GrantApplyVO.ResourceApplyItem item : resources) {
            if (status == REQUEST_APPROVED) {
                OpenApiResourcePo resource = requireAppableResource(item.getResourceId());
                item.setScopes(resolveRequestedScope(item.getScopes(), resource.getScopeCode()));
                validateResourceItem(item, app);
                upsertApprovedGrant(request, item, resource, environment, tenantId, currentUserId, reason);
            } else if (status == REQUEST_REJECTED) {
                markRejectedGrant(request, item, environment, tenantId, currentUserId, reason);
            }
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitAppLifecycleApply(Long appId) {
        Long currentUserId = SecurityContextHolder.getUserId();
        if (currentUserId == null) {
            throw new BusinessException("获取当前登录用户信息失败");
        }
        Long tenantId = requireTenantId();
        OpenAppPo app = requireOwnedApp(appId, tenantId, currentUserId);
        if (requiresSchoolScope(app.getScopes()) && !StringUtils.hasText(app.getSchoolScope())) {
            throw new BusinessException("请联系平台管理员配置授权学校后再提交开通申请");
        }
        int lifecycle = app.getLifecycleStatus() == null ? LIFECYCLE_DRAFT : app.getLifecycleStatus();
        int requestType;
        int pendingLifecycle;
        String environment;
        if (lifecycle == LIFECYCLE_DRAFT) {
            requestType = REQUEST_SANDBOX_OPEN;
            pendingLifecycle = LIFECYCLE_PENDING;
            environment = "SANDBOX";
        } else if (lifecycle == LIFECYCLE_TESTING) {
            requestType = REQUEST_PRODUCTION_OPEN;
            pendingLifecycle = LIFECYCLE_PRODUCTION_PENDING;
            environment = "PROD";
        } else {
            throw new BusinessException("当前应用状态不允许提交开通申请");
        }

        OpenAppPo update = new OpenAppPo();
        update.setId(appId);
        update.setLifecycleStatus(pendingLifecycle);
        update.setUpdateBy(currentUserId);
        int claimed = appMapper.update(update, new UpdateWrapper<OpenAppPo>()
                .eq("id", appId)
                .eq("tenant_id", tenantId)
                .eq("lifecycle_status", lifecycle));
        if (claimed != 1) {
            throw new BusinessException("应用状态已变化，请刷新后重试");
        }

        OpenAuthorizationRequestPo request = new OpenAuthorizationRequestPo();
        request.setTenantId(tenantId);
        request.setAppId(appId);
        request.setEnvironment(environment);
        request.setRequestType(requestType);
        request.setStatus(REQUEST_PENDING);
        request.setRequestData(environment);
        request.setApplicantId(currentUserId);
        request.setCreateBy(currentUserId);
        authorizationRequestMapper.insert(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewAppLifecycleApply(Long appId, Integer status, String reason) {
        validateReviewStatus(status);
        Long currentUserId = SecurityContextHolder.getUserId();
        if (currentUserId == null) {
            throw new BusinessException("获取当前登录用户信息失败");
        }
        Long tenantId = requireTenantId();
        requireAdministrator();
        OpenAppPo app = appMapper.selectOne(new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getId, appId)
                .eq(OpenAppPo::getTenantId, tenantId)
                .eq(OpenAppPo::getDelFlag, 0)
                .last("LIMIT 1"));
        if (app == null || app.getVendorId() == null) {
            throw new BusinessException("应用不存在或无权审核");
        }
        int lifecycle = app.getLifecycleStatus() == null ? -1 : app.getLifecycleStatus();
        int requestType = lifecycle == LIFECYCLE_PENDING ? REQUEST_SANDBOX_OPEN
                : lifecycle == LIFECYCLE_PRODUCTION_PENDING ? REQUEST_PRODUCTION_OPEN : -1;
        if (requestType < 0) {
            throw new BusinessException("当前应用不存在待审核的开通申请");
        }
        OpenAuthorizationRequestPo request = authorizationRequestMapper.selectOne(new LambdaQueryWrapper<OpenAuthorizationRequestPo>()
                .eq(OpenAuthorizationRequestPo::getTenantId, tenantId)
                .eq(OpenAuthorizationRequestPo::getAppId, appId)
                .eq(OpenAuthorizationRequestPo::getRequestType, requestType)
                .eq(OpenAuthorizationRequestPo::getStatus, REQUEST_PENDING)
                .eq(OpenAuthorizationRequestPo::getDelFlag, 0)
                .orderByDesc(OpenAuthorizationRequestPo::getCreateTime)
                .last("LIMIT 1"));
        if (request == null) {
            request = restoreLifecycleApply(app, tenantId, currentUserId, requestType);
            if (authorizationRequestMapper.insert(request) != 1) {
                throw new BusinessException("历史开通申请补齐失败");
            }
        }
        OpenAuthorizationRequestPo review = new OpenAuthorizationRequestPo();
        review.setStatus(status);
        review.setReviewReason(reason);
        review.setReviewerId(currentUserId);
        review.setReviewTime(LocalDateTime.now());
        review.setUpdateBy(currentUserId);
        int claimed = authorizationRequestMapper.update(review, new UpdateWrapper<OpenAuthorizationRequestPo>()
                .eq("id", request.getId())
                .eq("tenant_id", tenantId)
                .eq("status", REQUEST_PENDING));
        if (claimed != 1) {
            throw new BusinessException("申请已被其他管理员审核，请刷新后重试");
        }

        OpenAppPo update = new OpenAppPo();
        update.setId(appId);
        update.setLifecycleStatus(status == REQUEST_APPROVED
                ? requestType == REQUEST_SANDBOX_OPEN ? LIFECYCLE_SANDBOX : LIFECYCLE_PRODUCTION
                : requestType == REQUEST_SANDBOX_OPEN ? LIFECYCLE_DRAFT : LIFECYCLE_TESTING);
        if (status == REQUEST_APPROVED && requestType == REQUEST_PRODUCTION_OPEN) {
            update.setStatus(APP_STATUS_ENABLED);
        }
        update.setUpdateBy(currentUserId);
        appMapper.updateById(update);
        if (status == REQUEST_APPROVED) {
            activateLifecycleResources(app, request.getEnvironment(), currentUserId, reason);
        }
    }

    /**
     * 旧生命周期接口曾只更新应用状态，未创建审核记录；审核时补齐并保留修复痕迹。
     */
    private OpenAuthorizationRequestPo restoreLifecycleApply(OpenAppPo app, Long tenantId,
                                                               Long currentUserId, int requestType) {
        String environment = requestType == REQUEST_SANDBOX_OPEN ? "SANDBOX" : "PROD";
        Long applicantId = app.getCreateBy() == null ? currentUserId : app.getCreateBy();
        OpenAuthorizationRequestPo request = new OpenAuthorizationRequestPo();
        request.setTenantId(tenantId);
        request.setAppId(app.getId());
        request.setEnvironment(environment);
        request.setRequestType(requestType);
        request.setStatus(REQUEST_PENDING);
        request.setRequestData(environment);
        request.setReason("补齐历史开通申请记录");
        request.setApplicantId(applicantId);
        request.setCreateBy(applicantId);
        return request;
    }

    /**
     * 应用开通审核已包含其创建时选定的接口范围；审核通过后同步落为该环境的有效授权。
     * 后续新增接口仍走独立授权申请，已撤销或已驳回的授权不会被这里重新激活。
     */
    private void activateLifecycleResources(OpenAppPo app, String environment, Long reviewerId, String reviewReason) {
        if (app.getId() == null || app.getTenantId() == null || !StringUtils.hasText(app.getScopes())) {
            return;
        }
        Set<String> appScopes = java.util.Arrays.stream(app.getScopes().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (appScopes.isEmpty()) {
            return;
        }
        for (OpenApiResourcePo resource : resourceMapper.selectList(new LambdaQueryWrapper<OpenApiResourcePo>()
                .eq(OpenApiResourcePo::getStatus, 0)
                .eq(OpenApiResourcePo::getPublishStatus, 2)
                .eq(OpenApiResourcePo::getAllowApply, 1))) {
            if (!StringUtils.hasText(resource.getScopeCode()) || !appScopes.contains(resource.getScopeCode().trim())
                    || findGrant(app.getTenantId(), app.getId(), resource.getId(), environment) != null) {
                continue;
            }
            OpenAppResourceGrantPo grant = new OpenAppResourceGrantPo();
            grant.setTenantId(app.getTenantId());
            grant.setAppId(app.getId());
            grant.setResourceId(resource.getId());
            grant.setEnvironment(environment);
            grant.setScopes(resource.getScopeCode().trim());
            grant.setQuota(0L);
            grant.setStatus(GRANT_ACTIVE);
            grant.setApplyReason("随应用开通审核自动授权");
            grant.setReviewReason(reviewReason);
            grant.setReviewerId(reviewerId);
            grant.setReviewTime(LocalDateTime.now());
            grant.setCreateBy(reviewerId);
            grant.setUpdateBy(reviewerId);
            baseMapper.insert(grant);
        }
    }

    @Override
    public List<AppGrantDetailVO> listAppGrants(Long appId) {
        Long tenantId = requireTenantId();
        LambdaQueryWrapper<OpenAppResourceGrantPo> wrapper = new LambdaQueryWrapper<OpenAppResourceGrantPo>()
                .eq(OpenAppResourceGrantPo::getTenantId, tenantId)
                .eq(OpenAppResourceGrantPo::getAppId, appId)
                .eq(OpenAppResourceGrantPo::getStatus, 1) // 已生效
                .eq(OpenAppResourceGrantPo::getDelFlag, 0)
                .and(w -> w.gt(OpenAppResourceGrantPo::getExpiresAt, LocalDateTime.now())
                        .or()
                        .isNull(OpenAppResourceGrantPo::getExpiresAt));
        applyReadableAppScope(wrapper, OpenAppResourceGrantPo::getAppId,
                tenantId, SecurityContextHolder.getUserId());
        List<OpenAppResourceGrantPo> grants = baseMapper.selectList(wrapper);
        return grants.stream().map(OpenAppAuthorizationConverter::toGrantDetailVO)
                // 后续补充资源和版本的详细信息
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> listEffectiveResourceIds(Long appId) {
        return baseMapper.selectList(new LambdaQueryWrapper<OpenAppResourceGrantPo>()
                        .eq(OpenAppResourceGrantPo::getAppId, appId)
                        .eq(OpenAppResourceGrantPo::getStatus, 1) // 已生效
                        .eq(OpenAppResourceGrantPo::getDelFlag, 0)
                        .and(w -> w.gt(OpenAppResourceGrantPo::getExpiresAt, LocalDateTime.now())
                                .or()
                                .isNull(OpenAppResourceGrantPo::getExpiresAt)))
                .stream()
                .map(OpenAppResourceGrantPo::getResourceId)
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasPermission(Long appId, Long resourceId, String environment, String scope) {
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("获取当前租户信息失败");
        }
        environment = normalizeEnvironment(environment);
        if (!StringUtils.hasText(scope)) {
            return false;
        }
        if (!isRuntimeAppUsable(appId, tenantId)) {
            return false;
        }
        OpenAppResourceGrantPo grant = baseMapper.selectOne(new LambdaQueryWrapper<OpenAppResourceGrantPo>()
                .eq(OpenAppResourceGrantPo::getTenantId, tenantId)
                .eq(OpenAppResourceGrantPo::getAppId, appId)
                .eq(OpenAppResourceGrantPo::getResourceId, resourceId)
                .eq(OpenAppResourceGrantPo::getEnvironment, environment)
                .eq(OpenAppResourceGrantPo::getStatus, GRANT_ACTIVE)
                .eq(OpenAppResourceGrantPo::getDelFlag, 0)
                .last("LIMIT 1"));
        if (grant == null) { // 授权不存在或未生效
            return false;
        }
        // 检查是否过期
        if (grant.getExpiresAt() != null && grant.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        // 检查Scope是否包含
        if (!StringUtils.hasText(grant.getScopes())) {
            return false;
        }
        List<String> scopes = List.of(grant.getScopes().split(","));
        return scopes.contains(scope);
    }

    @Override
    public void requireActiveVendor(Long vendorId, Long tenantId) {
        if (vendorId == null) {
            return;
        }
        if (tenantId == null) {
            throw new BusinessException("获取当前租户信息失败");
        }
        if (vendorMapper == null) {
            throw new BusinessException("厂商状态校验未配置");
        }
        OpenVendorPo vendor = vendorMapper.selectOne(new LambdaQueryWrapper<OpenVendorPo>()
                .eq(OpenVendorPo::getId, vendorId)
                .eq(OpenVendorPo::getTenantId, tenantId)
                .eq(OpenVendorPo::getDelFlag, 0)
                .last("LIMIT 1"));
        if (vendor == null || !Objects.equals(vendor.getStatus(), VENDOR_STATUS_APPROVED)) {
            throw new BusinessException("厂商不存在或已停用");
        }
    }

    @Override
    public String resolveAuthorizedDataScope(Long tenantId, Long appId, String environment, String scope) {
        if (tenantId == null) {
            throw new BusinessException("获取当前租户信息失败");
        }
        if (!isRuntimeAppUsable(appId, tenantId)) {
            return null;
        }
        environment = normalizeEnvironment(environment);
        if (!StringUtils.hasText(scope)) {
            return null;
        }
        List<OpenAppResourceGrantPo> grants = baseMapper.selectList(new LambdaQueryWrapper<OpenAppResourceGrantPo>()
                .eq(OpenAppResourceGrantPo::getTenantId, tenantId)
                .eq(OpenAppResourceGrantPo::getAppId, appId)
                .eq(OpenAppResourceGrantPo::getEnvironment, environment)
                .eq(OpenAppResourceGrantPo::getStatus, GRANT_ACTIVE)
                .eq(OpenAppResourceGrantPo::getDelFlag, 0));
        for (OpenAppResourceGrantPo grant : grants) {
            if (isExpired(grant.getExpiresAt()) || !containsScope(grant.getScopes(), scope)) {
                continue;
            }
            OpenApiResourcePo resource = resourceMapper.selectOne(new LambdaQueryWrapper<OpenApiResourcePo>()
                    .eq(OpenApiResourcePo::getId, grant.getResourceId())
                    .eq(OpenApiResourcePo::getStatus, 0)
                    .eq(OpenApiResourcePo::getPublishStatus, 2)
                    .last("LIMIT 1"));
            if (resource != null && scope.equals(resource.getScopeCode() == null
                    ? null : resource.getScopeCode().trim())) {
                return grant.getDataScope() == null ? "" : grant.getDataScope();
            }
        }
        return null;
    }

    @Override
    public String resolveAuthorizedDataScope(Long tenantId, Long appId, String environment,
                                             String scope, String resourceCode) {
        if (tenantId == null) {
            throw new BusinessException("获取当前租户信息失败");
        }
        if (!isRuntimeAppUsable(appId, tenantId)) {
            return null;
        }
        environment = normalizeEnvironment(environment);
        if (!StringUtils.hasText(scope) || !StringUtils.hasText(resourceCode)) {
            return null;
        }
        List<OpenApiResourcePo> resources = resourceMapper.selectList(new LambdaQueryWrapper<OpenApiResourcePo>()
                .eq(OpenApiResourcePo::getResourceCode, resourceCode.trim())
                .eq(OpenApiResourcePo::getStatus, 0)
                .eq(OpenApiResourcePo::getPublishStatus, 2)
                .last("LIMIT 2"));
        if (resources == null || resources.size() != 1) {
            return null;
        }
        OpenApiResourcePo resource = resources.get(0);
        if (!StringUtils.hasText(resource.getScopeCode())
                || !scope.trim().equals(resource.getScopeCode().trim())) {
            return null;
        }
        OpenAppResourceGrantPo grant = baseMapper.selectOne(new LambdaQueryWrapper<OpenAppResourceGrantPo>()
                .eq(OpenAppResourceGrantPo::getTenantId, tenantId)
                .eq(OpenAppResourceGrantPo::getAppId, appId)
                .eq(OpenAppResourceGrantPo::getResourceId, resource.getId())
                .eq(OpenAppResourceGrantPo::getEnvironment, environment)
                .eq(OpenAppResourceGrantPo::getStatus, GRANT_ACTIVE)
                .eq(OpenAppResourceGrantPo::getDelFlag, 0)
                .last("LIMIT 1"));
        if (grant == null || isExpired(grant.getExpiresAt()) || !containsScope(grant.getScopes(), scope.trim())) {
            return null;
        }
        return grant.getDataScope() == null ? "" : grant.getDataScope();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean revokeGrant(Long grantId, String reason) {
        OpenAppResourceGrantPo grant = getById(grantId);
        if (grant == null) {
            throw new BusinessException("授权记录不存在");
        }
        if (!Integer.valueOf(GRANT_ACTIVE).equals(grant.getStatus())) { // 只能撤销已生效的授权
            throw new BusinessException("授权状态不正确，无法撤销");
        }

        Long currentUserId = SecurityContextHolder.getUserId();
        if (currentUserId == null) {
            throw new BusinessException("获取当前登录用户信息失败");
        }
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("获取当前租户信息失败");
        }
        if (grant.getTenantId() != null && !tenantId.equals(grant.getTenantId())) {
            throw new BusinessException("无权操作其他租户的授权");
        }
        OpenAppPo app = appMapper.selectOne(new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getId, grant.getAppId())
                .eq(OpenAppPo::getTenantId, tenantId)
                .eq(OpenAppPo::getDelFlag, 0)
                .last("LIMIT 1"));
        if (app == null) {
            throw new BusinessException("应用不存在或无权操作授权");
        }
        requireVendorWriter(app, tenantId, currentUserId);

        grant.setStatus(GRANT_REVOKED); // 已撤销
        grant.setReviewReason(reason);
        grant.setReviewTime(LocalDateTime.now());
        grant.setReviewerId(currentUserId);
        grant.setUpdateBy(currentUserId);
        return updateById(grant);
    }

    private OpenAppPo requireOwnedApp(Long appId, Long tenantId, Long userId) {
        OpenAppPo app = appMapper.selectOne(new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getId, appId)
                .eq(OpenAppPo::getTenantId, tenantId)
                .eq(OpenAppPo::getStatus, 0)
                .eq(OpenAppPo::getDelFlag, 0)
                .last("LIMIT 1"));
        if (app == null || app.getVendorId() == null || !tenantId.equals(app.getTenantId())) {
            throw new BusinessException("应用不存在或无权申请授权");
        }
        if (isAdministrator()) {
            return app;
        }
        OpenVendorUserPo membership = vendorUserMapper.selectOne(new LambdaQueryWrapper<OpenVendorUserPo>()
                .eq(OpenVendorUserPo::getTenantId, tenantId)
                .eq(OpenVendorUserPo::getVendorId, app.getVendorId())
                .eq(OpenVendorUserPo::getUserId, userId)
                .eq(OpenVendorUserPo::getStatus, 0)
                .eq(OpenVendorUserPo::getDelFlag, 0)
                .last("LIMIT 1"));
        if (membership == null) {
            throw new BusinessException("当前用户不是该厂商成员，无权申请应用授权");
        }
        requireWriterRole(membership);
        return app;
    }

    private OpenApiResourcePo requireAppableResource(Long resourceId) {
        OpenApiResourcePo resource = resourceMapper.selectOne(new LambdaQueryWrapper<OpenApiResourcePo>()
                .eq(OpenApiResourcePo::getId, resourceId)
                .eq(OpenApiResourcePo::getStatus, 0)
                .eq(OpenApiResourcePo::getPublishStatus, 2)
                .eq(OpenApiResourcePo::getAllowApply, 1)
                .last("LIMIT 1"));
        if (resource == null) {
            throw new BusinessException("资源不存在、未发布或不允许申请");
        }
        return resource;
    }

    private OpenAppResourceGrantPo findGrant(Long tenantId, Long appId, Long resourceId, String environment) {
        return baseMapper.selectOne(new LambdaQueryWrapper<OpenAppResourceGrantPo>()
                .eq(OpenAppResourceGrantPo::getTenantId, tenantId)
                .eq(OpenAppResourceGrantPo::getAppId, appId)
                .eq(OpenAppResourceGrantPo::getResourceId, resourceId)
                .eq(OpenAppResourceGrantPo::getEnvironment, environment)
                .eq(OpenAppResourceGrantPo::getDelFlag, 0)
                .last("LIMIT 1"));
    }

    private void applyPendingGrant(OpenAppResourceGrantPo grant, GrantApplyVO applyVO,
                                   GrantApplyVO.ResourceApplyItem item, Long tenantId, Long userId) {
        grant.setTenantId(tenantId);
        grant.setAppId(applyVO.getAppId());
        grant.setResourceId(item.getResourceId());
        grant.setEnvironment(applyVO.getEnvironment());
        grant.setScopes(item.getScopes());
        grant.setDataScope(item.getDataScope());
        grant.setQuota(item.getQuota() == null ? 0L : item.getQuota());
        grant.setExpiresAt(expireAt(item));
        grant.setStatus(GRANT_PENDING);
        grant.setApplyReason(applyVO.getApplyReason());
        grant.setReviewReason(null);
        grant.setReviewerId(null);
        grant.setReviewTime(null);
        if (grant.getCreateBy() == null) {
            grant.setCreateBy(userId);
        }
        grant.setUpdateBy(userId);
    }

    private void upsertApprovedGrant(OpenAuthorizationRequestPo request, GrantApplyVO.ResourceApplyItem item,
                                     OpenApiResourcePo resource, String environment, Long tenantId,
                                     Long reviewerId, String reviewReason) {
        OpenAppResourceGrantPo grant = findGrant(tenantId, request.getAppId(), item.getResourceId(), environment);
        if (grant == null) {
            grant = new OpenAppResourceGrantPo();
        } else if (Integer.valueOf(GRANT_ACTIVE).equals(grant.getStatus()) && !isExpired(grant.getExpiresAt())) {
            throw new BusinessException("授权记录已被其他申请更新，请刷新后重试");
        }
        grant.setTenantId(tenantId);
        grant.setAppId(request.getAppId());
        grant.setResourceId(item.getResourceId());
        grant.setEnvironment(environment);
        grant.setScopes(resource.getScopeCode().trim());
        grant.setDataScope(item.getDataScope());
        grant.setQuota(item.getQuota() == null ? 0L : item.getQuota());
        grant.setExpiresAt(expireAt(item));
        grant.setStatus(GRANT_ACTIVE);
        grant.setApplyReason(request.getReason());
        grant.setReviewReason(reviewReason);
        grant.setReviewerId(reviewerId);
        grant.setReviewTime(LocalDateTime.now());
        if (grant.getCreateBy() == null) {
            grant.setCreateBy(request.getApplicantId());
        }
        grant.setUpdateBy(reviewerId);
        if (grant.getId() == null) {
            baseMapper.insert(grant);
        } else {
            baseMapper.updateById(grant);
        }
    }

    private void markRejectedGrant(OpenAuthorizationRequestPo request, GrantApplyVO.ResourceApplyItem item,
                                   String environment, Long tenantId, Long reviewerId, String reviewReason) {
        OpenAppResourceGrantPo grant = findGrant(tenantId, request.getAppId(), item.getResourceId(), environment);
        if (grant == null) {
            grant = new OpenAppResourceGrantPo();
            grant.setTenantId(tenantId);
            grant.setAppId(request.getAppId());
            grant.setResourceId(item.getResourceId());
            grant.setEnvironment(environment);
            grant.setScopes(item.getScopes());
            grant.setDataScope(item.getDataScope());
            grant.setQuota(item.getQuota() == null ? 0L : item.getQuota());
            grant.setExpiresAt(expireAt(item));
            grant.setCreateBy(request.getApplicantId());
            grant.setApplyReason(request.getReason());
            grant.setStatus(GRANT_REJECTED);
            grant.setReviewReason(reviewReason);
            grant.setReviewerId(reviewerId);
            grant.setReviewTime(LocalDateTime.now());
            grant.setUpdateBy(reviewerId);
            baseMapper.insert(grant);
            return;
        }
        if (!Integer.valueOf(GRANT_PENDING).equals(grant.getStatus())) {
            return;
        }
        grant.setStatus(GRANT_REJECTED);
        grant.setReviewReason(reviewReason);
        grant.setReviewerId(reviewerId);
        grant.setReviewTime(LocalDateTime.now());
        grant.setUpdateBy(reviewerId);
        baseMapper.updateById(grant);
    }

    private List<GrantApplyVO.ResourceApplyItem> readRequestResources(OpenAuthorizationRequestPo request) {
        if (!StringUtils.hasText(request.getRequestData())) {
            return List.of();
        }
        try {
            List<GrantApplyVO.ResourceApplyItem> resources = objectMapper.readValue(
                    request.getRequestData(), new TypeReference<List<GrantApplyVO.ResourceApplyItem>>() {});
            if (resources == null || resources.stream().anyMatch(item -> item == null || item.getResourceId() == null)) {
                throw new BusinessException("授权申请资源数据非法");
            }
            return resources;
        } catch (JsonProcessingException e) {
            throw new BusinessException("500", "反序列化资源申请数据失败", e);
        }
    }

    private void validateResourceItem(GrantApplyVO.ResourceApplyItem item, OpenAppPo app) {
        if (item.getQuota() != null && item.getQuota() < 0) {
            throw new BusinessException("调用配额不能为负数");
        }
        if (item.getExpireDays() != null && item.getExpireDays() < 0) {
            throw new BusinessException("有效期不能为负数");
        }
        validateDataScope(item.getDataScope(), app.getSchoolScope());
    }

    private void validateDataScope(String dataScope, String appSchoolScope) {
        if (!StringUtils.hasText(dataScope)) {
            return;
        }
        final Map<String, Object> scope;
        try {
            scope = objectMapper.readValue(dataScope, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new BusinessException("授权数据范围格式非法");
        }
        if (!scope.containsKey("schoolIds")) {
            return;
        }
        Object raw = scope.get("schoolIds");
        if (!(raw instanceof Collection<?> values)) {
            throw new BusinessException("授权学校范围格式非法");
        }
        Set<Long> allowed = parseSchoolIds(appSchoolScope);
        for (Object value : values) {
            long schoolId = parseSchoolId(value);
            if (!allowed.contains(schoolId)) {
                throw new BusinessException("资源数据范围不能超出应用授权学校");
            }
        }
    }

    private Set<Long> parseSchoolIds(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        try {
            return java.util.Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(Long::valueOf)
                    .filter(id -> id > 0L)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (NumberFormatException e) {
            throw new BusinessException("应用授权学校范围格式非法");
        }
    }

    private long parseSchoolId(Object value) {
        try {
            long id;
            if (value instanceof Number number) {
                double numeric = number.doubleValue();
                if (!Double.isFinite(numeric) || numeric != Math.rint(numeric)) {
                    throw new NumberFormatException();
                }
                id = number.longValue();
            } else if (value instanceof String text && StringUtils.hasText(text)) {
                id = Long.parseLong(text.trim());
            } else {
                throw new NumberFormatException();
            }
            if (id <= 0L) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new BusinessException("授权学校范围格式非法");
        }
    }

    private static boolean requiresSchoolScope(String scopes) {
        return scopes != null && java.util.Arrays.stream(scopes.split(","))
                .map(String::trim)
                .anyMatch(item -> item.startsWith("classroom.")
                        || item.equals("edu.teacher.read")
                        || item.equals("edu.student.read")
                        || item.equals("edu.device.read"));
    }

    private String resolveRequestedScope(String requestedScopes, String resourceScope) {
        if (!StringUtils.hasText(resourceScope) || !StringUtils.hasText(requestedScopes)) {
            throw new BusinessException("授权Scope不能为空");
        }
        String canonicalScope = resourceScope.trim();
        boolean valid = java.util.Arrays.stream(requestedScopes.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .allMatch(canonicalScope::equals);
        if (!valid) {
            throw new BusinessException("申请Scope必须匹配资源目录Scope");
        }
        return canonicalScope;
    }

    private String normalizeEnvironment(String environment) {
        if (!StringUtils.hasText(environment)) {
            throw new BusinessException("环境类型不能为空");
        }
        String normalized = environment.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ENVIRONMENTS.contains(normalized)) {
            throw new BusinessException("环境类型仅支持SANDBOX或PROD");
        }
        return normalized;
    }

    private void validateReviewStatus(Integer status) {
        if (status == null || (status != REQUEST_APPROVED && status != REQUEST_REJECTED)) {
            throw new BusinessException("审核状态仅支持通过或驳回");
        }
    }

    private LocalDateTime expireAt(GrantApplyVO.ResourceApplyItem item) {
        return item.getExpireDays() != null && item.getExpireDays() > 0
                ? LocalDateTime.now().plusDays(item.getExpireDays()) : null;
    }

    private boolean isExpired(LocalDateTime expiresAt) {
        return expiresAt != null && !expiresAt.isAfter(LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppCredentialVO generateCredential(Long appId, String environment) {
        Long currentUserId = SecurityContextHolder.getUserId();
        if (currentUserId == null) {
            throw new BusinessException("获取当前登录用户信息失败");
        }
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("获取当前租户信息失败");
        }
        environment = normalizeEnvironment(environment);
        OpenAppPo app = requireCredentialOwnedApp(appId, tenantId, currentUserId);
        ensureCredentialEnvironment(app, environment);

        // 检查是否已有有效凭证
        List<OpenAppCredentialPo> existCreds = appCredentialMapper.selectList(new LambdaQueryWrapper<OpenAppCredentialPo>()
                .eq(OpenAppCredentialPo::getTenantId, tenantId)
                .eq(OpenAppCredentialPo::getAppId, appId)
                .eq(OpenAppCredentialPo::getEnvironment, environment)
                .eq(OpenAppCredentialPo::getDelFlag, 0));
        for (OpenAppCredentialPo cred : existCreds) {
            if (cred.getStatus() == 0) { // 已有正常凭证，不能重复生成
                throw new BusinessException(environment + "环境已有有效凭证，请勿重复生成");
            }
        }

        // 生成ClientId和Secret
        String clientId = "APP_" + java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String clientSecret = java.util.UUID.randomUUID().toString().replace("-", "") + java.util.UUID.randomUUID().toString().replace("-", "");
        String secretHash = passwordEncoder.encode(clientSecret);

        // 保存凭证
        OpenAppCredentialPo credential = new OpenAppCredentialPo();
        credential.setTenantId(tenantId);
        credential.setAppId(appId);
        credential.setEnvironment(environment);
        credential.setClientId(clientId);
        credential.setClientSecretHash(secretHash);
        credential.setStatus(0); // 正常
        credential.setExpireAt(LocalDateTime.now().plusYears(1)); // 默认1年有效期
        credential.setCreateBy(currentUserId);
        appCredentialMapper.insert(credential);

        // 返回凭证，包含明文Secret
        return OpenAppAuthorizationConverter.toCredentialVO(credential, clientSecret);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppCredentialVO rotateCredential(Long credentialId) {
        OpenAppCredentialPo oldCredential = appCredentialMapper.selectById(credentialId);
        if (oldCredential == null) {
            throw new BusinessException("凭证不存在");
        }
        if (oldCredential.getStatus() != 0) { // 只能轮换正常状态的凭证
            throw new BusinessException("凭证状态不正确，无法轮换");
        }

        Long currentUserId = SecurityContextHolder.getUserId();
        if (currentUserId == null) {
            throw new BusinessException("获取当前登录用户信息失败");
        }
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("获取当前租户信息失败");
        }
        if (oldCredential.getTenantId() != null && !tenantId.equals(oldCredential.getTenantId())) {
            throw new BusinessException("无权操作其他租户应用凭证");
        }
        OpenAppPo app = requireCredentialOwnedApp(oldCredential.getAppId(), tenantId, currentUserId);
        ensureCredentialEnvironment(app, normalizeEnvironment(oldCredential.getEnvironment()));

        // 标记旧凭证为已轮换
        oldCredential.setStatus(2); // 已轮换
        oldCredential.setRotatedAt(LocalDateTime.now());
        oldCredential.setUpdateBy(currentUserId);
        appCredentialMapper.updateById(oldCredential);

        // 生成新凭证
        String clientId = "APP_" + java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String clientSecret = java.util.UUID.randomUUID().toString().replace("-", "") + java.util.UUID.randomUUID().toString().replace("-", "");
        String secretHash = passwordEncoder.encode(clientSecret);

        OpenAppCredentialPo newCredential = new OpenAppCredentialPo();
        newCredential.setTenantId(tenantId);
        newCredential.setAppId(oldCredential.getAppId());
        newCredential.setEnvironment(oldCredential.getEnvironment());
        newCredential.setClientId(clientId);
        newCredential.setClientSecretHash(secretHash);
        newCredential.setStatus(0); // 正常
        newCredential.setExpireAt(LocalDateTime.now().plusYears(1));
        newCredential.setCreateBy(currentUserId);
        appCredentialMapper.insert(newCredential);

        return OpenAppAuthorizationConverter.toCredentialVO(newCredential, clientSecret);
    }

    @Override
    public Long validateCredential(String clientId, String clientSecret) {
        CredentialContext context = validateCredentialContext(clientId, clientSecret);
        return context == null ? null : context.appId();
    }

    @Override
    public CredentialContext validateCredentialContext(String clientId, String clientSecret) {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            return null;
        }
        OpenAppCredentialPo credential = appCredentialMapper.selectOne(new LambdaQueryWrapper<OpenAppCredentialPo>()
                .eq(OpenAppCredentialPo::getClientId, clientId.trim())
                .eq(OpenAppCredentialPo::getDelFlag, 0)
                .last("LIMIT 1"));
        if (credential == null || !Integer.valueOf(0).equals(credential.getStatus())
                || credential.getAppId() == null || !StringUtils.hasText(credential.getEnvironment())
                || !StringUtils.hasText(credential.getClientSecretHash())
                || (credential.getExpireAt() != null && !credential.getExpireAt().isAfter(LocalDateTime.now()))
                || !passwordEncoder.matches(clientSecret, credential.getClientSecretHash())) {
            return null;
        }
        String environment;
        try {
            environment = normalizeEnvironment(credential.getEnvironment());
        } catch (BusinessException e) {
            return null;
        }
        return new CredentialContext(credential.getAppId(), credential.getClientId(), environment);
    }

    private OpenAppPo requireCredentialOwnedApp(Long appId, Long tenantId, Long userId) {
        OpenAppPo app = appMapper.selectOne(new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getId, appId)
                .eq(OpenAppPo::getTenantId, tenantId)
                .eq(OpenAppPo::getStatus, APP_STATUS_ENABLED)
                .eq(OpenAppPo::getDelFlag, 0)
                .last("LIMIT 1"));
        if (app == null) {
            throw new BusinessException("应用不存在或无权操作凭证");
        }
        if (!tenantId.equals(app.getTenantId())) {
            throw new BusinessException("无权操作其他租户应用凭证");
        }
        if (isAdministrator()) {
            return app;
        }
        if (app.getVendorId() == null) {
            throw new BusinessException("无权操作非厂商应用凭证");
        }
        OpenVendorUserPo membership = vendorUserMapper.selectOne(new LambdaQueryWrapper<OpenVendorUserPo>()
                .eq(OpenVendorUserPo::getTenantId, tenantId)
                .eq(OpenVendorUserPo::getVendorId, app.getVendorId())
                .eq(OpenVendorUserPo::getUserId, userId)
                .eq(OpenVendorUserPo::getStatus, 0)
                .eq(OpenVendorUserPo::getDelFlag, 0)
                .last("LIMIT 1"));
        if (membership == null) {
            throw new BusinessException("当前用户无权操作该应用凭证");
        }
        requireWriterRole(membership);
        return app;
    }

    private void ensureCredentialEnvironment(OpenAppPo app, String environment) {
        if (app.getVendorId() == null) {
            if (!"PROD".equals(environment)) {
                throw new BusinessException("非厂商旧应用仅支持PROD凭证");
            }
            return;
        }
        int lifecycle = app.getLifecycleStatus() == null ? -1 : app.getLifecycleStatus();
        if ("SANDBOX".equals(environment)
                && (lifecycle < LIFECYCLE_SANDBOX || lifecycle == LIFECYCLE_SUSPENDED || lifecycle == LIFECYCLE_REVOKED)) {
            throw new BusinessException("应用尚未开通沙箱环境");
        }
        if ("PROD".equals(environment) && lifecycle != LIFECYCLE_PRODUCTION) {
            throw new BusinessException("应用尚未开通生产环境");
        }
    }

    private boolean containsScope(String scopes, String scope) {
        if (!StringUtils.hasText(scopes)) {
            return false;
        }
        return java.util.Arrays.stream(scopes.split(","))
                .map(String::trim)
                .anyMatch(scope::equals);
    }

    private <T> void applyReadableAppScope(LambdaQueryWrapper<T> wrapper,
                                           SFunction<T, ?> appIdColumn,
                                           Long tenantId, Long userId) {
        if (isAdministrator()) {
            return;
        }
        List<Long> appIds = readableAppIds(tenantId, userId);
        if (appIds.isEmpty()) {
            wrapper.eq(appIdColumn, -1L);
        } else {
            wrapper.in(appIdColumn, appIds);
        }
    }

    private List<Long> readableAppIds(Long tenantId, Long userId) {
        if (userId == null) {
            throw new BusinessException("获取当前登录用户信息失败");
        }
        List<OpenVendorUserPo> memberships = vendorUserMapper.selectList(new LambdaQueryWrapper<OpenVendorUserPo>()
                .eq(OpenVendorUserPo::getTenantId, tenantId)
                .eq(OpenVendorUserPo::getUserId, userId)
                .eq(OpenVendorUserPo::getStatus, 0)
                .eq(OpenVendorUserPo::getDelFlag, 0));
        if (memberships == null || memberships.isEmpty()) {
            return List.of();
        }
        List<Long> vendorIds = memberships.stream()
                .map(OpenVendorUserPo::getVendorId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (vendorIds.isEmpty()) {
            return List.of();
        }
        List<OpenAppPo> apps = appMapper.selectList(new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getTenantId, tenantId)
                .in(OpenAppPo::getVendorId, vendorIds)
                .eq(OpenAppPo::getDelFlag, 0));
        return apps == null ? List.of() : apps.stream()
                .map(OpenAppPo::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private void requireVendorWriter(OpenAppPo app, Long tenantId, Long userId) {
        if (app == null || app.getVendorId() == null) {
            throw new BusinessException("无权操作非厂商应用");
        }
        if (app.getTenantId() != null && !tenantId.equals(app.getTenantId())) {
            throw new BusinessException("无权操作其他租户应用");
        }
        if (isAdministrator()) {
            return;
        }
        OpenVendorUserPo membership = vendorUserMapper.selectOne(new LambdaQueryWrapper<OpenVendorUserPo>()
                .eq(OpenVendorUserPo::getTenantId, tenantId)
                .eq(OpenVendorUserPo::getVendorId, app.getVendorId())
                .eq(OpenVendorUserPo::getUserId, userId)
                .eq(OpenVendorUserPo::getStatus, 0)
                .eq(OpenVendorUserPo::getDelFlag, 0)
                .last("LIMIT 1"));
        if (membership == null) {
            throw new BusinessException("当前用户不是该厂商成员，无权操作应用");
        }
        requireWriterRole(membership);
    }

    private void requireWriterRole(OpenVendorUserPo membership) {
        if (membership == null || !("OWNER".equalsIgnoreCase(membership.getRole())
                || "DEVELOPER".equalsIgnoreCase(membership.getRole()))) {
            throw new BusinessException("当前厂商用户仅可查看应用");
        }
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

    private boolean isRuntimeAppUsable(Long appId, Long tenantId) {
        OpenAppPo app = appMapper.selectOne(new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getId, appId)
                .eq(OpenAppPo::getTenantId, tenantId)
                .eq(OpenAppPo::getStatus, APP_STATUS_ENABLED)
                .eq(OpenAppPo::getDelFlag, 0)
                .last("LIMIT 1"));
        if (app == null) {
            return false;
        }
        if (app.getVendorId() == null) {
            return true;
        }
        try {
            requireActiveVendor(app.getVendorId(), tenantId);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    private void requireAdministrator() {
        if (!isAdministrator()) {
            throw new BusinessException("仅管理员可审核授权申请");
        }
    }

    private Long requireTenantId() {
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("获取当前租户信息失败");
        }
        return tenantId;
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }
}
