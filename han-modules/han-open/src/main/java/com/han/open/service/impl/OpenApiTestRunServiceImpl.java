package com.han.open.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.HanIdUtil;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.open.converter.OpenApiTestRunConverter;
import com.han.open.domain.dto.OpenApiTestRunDTO;
import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.domain.po.OpenApiTestRunPo;
import com.han.open.domain.po.OpenAppPo;
import com.han.open.domain.po.OpenVendorUserPo;
import com.han.open.domain.vo.OpenApiTestRunVO;
import com.han.open.mapper.OpenApiResourceMapper;
import com.han.open.mapper.OpenApiTestRunMapper;
import com.han.open.mapper.OpenAppMapper;
import com.han.open.mapper.OpenVendorUserMapper;
import com.han.open.service.OpenApiTestRunService;
import com.han.open.service.OpenAppAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** 在线调测审计服务实现。 */
@Service
@RequiredArgsConstructor
public class OpenApiTestRunServiceImpl extends ServiceImpl<OpenApiTestRunMapper, OpenApiTestRunPo>
        implements OpenApiTestRunService {

    private static final Set<String> ADMIN_ROLES = Set.of("admin", "tenantAdmin");
    private static final Set<String> ALLOWED_ENVIRONMENTS = Set.of("SANDBOX", "PROD");
    private static final Set<String> WRITER_ROLES = Set.of("OWNER", "DEVELOPER");
    private static final Set<Integer> TERMINAL_APP_LIFECYCLES = Set.of(6, 7);

    private final OpenAppMapper appMapper;
    private final OpenVendorUserMapper vendorUserMapper;
    private final OpenApiResourceMapper resourceMapper;
    private final OpenAppAuthorizationService authorizationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenApiTestRunVO add(OpenApiTestRunDTO request) {
        if (request == null) {
            throw new BusinessException("调测结果不能为空");
        }
        Long tenantId = requireTenantId();
        Long userId = requireUserId();
        String environment = normalizeEnvironment(request.getEnvironment());
        OpenAppPo app = requireApp(request.getAppId(), tenantId, userId, true);
        OpenApiResourcePo resource = requireTestableResource(request.getResourceId());
        if (!StringUtils.hasText(resource.getScopeCode())
                || !authorizationService.hasPermission(app.getId(), resource.getId(), environment,
                resource.getScopeCode().trim())) {
            throw new BusinessException("应用未获得该环境的有效接口授权");
        }

        int statusCode = requireStatusCode(request.getStatusCode());
        int durationMs = requireDuration(request.getDurationMs());
        long responseSize = requireResponseSize(request.getResponseSize());
        String result = statusCode >= 200 && statusCode < 400 ? "SUCCESS" : "FAIL";

        OpenApiTestRunPo record = new OpenApiTestRunPo();
        record.setId(HanIdUtil.snowflakeId());
        record.setTenantId(tenantId);
        record.setVendorId(app.getVendorId());
        record.setAppId(app.getId());
        record.setResourceId(resource.getId());
        record.setEnvironment(environment);
        // 方法和路径只能取已发布目录，禁止使用浏览器提交的值。
        record.setRequestMethod(resource.getHttpMethod().trim().toUpperCase(Locale.ROOT));
        record.setRequestPath(resource.getPath().trim());
        record.setStatusCode(statusCode);
        record.setResult(result);
        record.setTraceId(resolveTraceId(request.getTraceId()));
        record.setDurationMs(durationMs);
        record.setRedactedSummary("status=" + statusCode + ",bytes=" + responseSize);
        record.setCreateTime(LocalDateTime.now());
        if (baseMapper.insert(record) != 1) {
            throw new BusinessException("调测记录保存失败");
        }
        return OpenApiTestRunConverter.toVO(record);
    }

    @Override
    public List<OpenApiTestRunVO> list(Long appId) {
        Long tenantId = requireTenantId();
        Long userId = requireUserId();
        List<Long> appIds;
        if (appId != null) {
            OpenAppPo app = requireApp(appId, tenantId, userId, false);
            appIds = List.of(app.getId());
        } else if (isAdministrator()) {
            List<OpenAppPo> apps = appMapper.selectList(new LambdaQueryWrapper<OpenAppPo>()
                            .eq(OpenAppPo::getTenantId, tenantId)
                            .eq(OpenAppPo::getDelFlag, 0));
            appIds = apps == null ? List.of() : apps.stream()
                    .map(OpenAppPo::getId).filter(java.util.Objects::nonNull).toList();
        } else {
            appIds = readableAppIds(tenantId, userId);
        }
        if (appIds.isEmpty()) {
            return List.of();
        }
        List<OpenApiTestRunPo> records = baseMapper.selectList(new LambdaQueryWrapper<OpenApiTestRunPo>()
                .eq(OpenApiTestRunPo::getTenantId, tenantId)
                .in(OpenApiTestRunPo::getAppId, appIds)
                .orderByDesc(OpenApiTestRunPo::getCreateTime)
                .last("LIMIT 50"));
        if (records == null) {
            return List.of();
        }
        return records.stream().limit(50).map(OpenApiTestRunConverter::toVO).toList();
    }

    private OpenAppPo requireApp(Long appId, Long tenantId, Long userId, boolean writer) {
        if (appId == null) {
            throw new BusinessException("应用ID不能为空");
        }
        OpenAppPo app = appMapper.selectOne(new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getId, appId)
                .eq(OpenAppPo::getTenantId, tenantId)
                .eq(OpenAppPo::getDelFlag, 0)
                .eq(OpenAppPo::getStatus, 0)
                .last("LIMIT 1"));
        if (app == null || app.getVendorId() == null) {
            throw new BusinessException("应用不存在或无权调测");
        }
        if (app.getLifecycleStatus() != null && TERMINAL_APP_LIFECYCLES.contains(app.getLifecycleStatus())) {
            throw new BusinessException("应用当前生命周期不可调测");
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
            throw new BusinessException("当前用户无权访问该厂商应用");
        }
        if (writer && !WRITER_ROLES.contains(normalizeRole(membership.getRole()))) {
            throw new BusinessException("当前厂商用户仅可查看调测记录");
        }
        return app;
    }

    private OpenApiResourcePo requireTestableResource(Long resourceId) {
        if (resourceId == null) {
            throw new BusinessException("资源ID不能为空");
        }
        OpenApiResourcePo resource = resourceMapper.selectOne(new LambdaQueryWrapper<OpenApiResourcePo>()
                .eq(OpenApiResourcePo::getId, resourceId)
                .eq(OpenApiResourcePo::getStatus, 0)
                .eq(OpenApiResourcePo::getPublishStatus, 2)
                .eq(OpenApiResourcePo::getAllowTest, 1)
                .last("LIMIT 1"));
        if (resource == null) {
            throw new BusinessException("资源不存在、未发布或不允许调测");
        }
        if (!StringUtils.hasText(resource.getHttpMethod()) || !StringUtils.hasText(resource.getPath())) {
            throw new BusinessException("资源目录缺少请求定义");
        }
        return resource;
    }

    private List<Long> readableAppIds(Long tenantId, Long userId) {
        List<OpenVendorUserPo> memberships = vendorUserMapper.selectList(new LambdaQueryWrapper<OpenVendorUserPo>()
                .eq(OpenVendorUserPo::getTenantId, tenantId)
                .eq(OpenVendorUserPo::getUserId, userId)
                .eq(OpenVendorUserPo::getStatus, 0)
                .eq(OpenVendorUserPo::getDelFlag, 0));
        if (memberships == null || memberships.isEmpty()) {
            return List.of();
        }
        List<Long> vendorIds = memberships.stream().map(OpenVendorUserPo::getVendorId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (vendorIds.isEmpty()) {
            return List.of();
        }
        List<OpenAppPo> apps = appMapper.selectList(new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getTenantId, tenantId)
                .in(OpenAppPo::getVendorId, vendorIds)
                .eq(OpenAppPo::getDelFlag, 0));
        return apps == null ? List.of() : apps.stream().map(OpenAppPo::getId)
                .filter(java.util.Objects::nonNull).distinct().toList();
    }

    private Long requireTenantId() {
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("获取当前租户信息失败");
        }
        return tenantId;
    }

    private Long requireUserId() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("获取当前登录用户信息失败");
        }
        return userId;
    }

    private String normalizeEnvironment(String environment) {
        String normalized = environment == null ? "" : environment.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ENVIRONMENTS.contains(normalized)) {
            throw new BusinessException("环境类型仅支持SANDBOX或PROD");
        }
        return normalized;
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }

    private int requireStatusCode(Integer statusCode) {
        if (statusCode == null || statusCode < 0 || statusCode > 599) {
            throw new BusinessException("响应状态码不合法");
        }
        return statusCode;
    }

    private int requireDuration(Integer durationMs) {
        if (durationMs == null || durationMs < 0 || durationMs > 600000) {
            throw new BusinessException("请求耗时不合法");
        }
        return durationMs;
    }

    private long requireResponseSize(Long responseSize) {
        if (responseSize == null || responseSize < 0 || responseSize > 50000000L) {
            throw new BusinessException("响应大小不合法");
        }
        return responseSize;
    }

    private String resolveTraceId(String traceId) {
        return StringUtils.hasText(traceId) ? traceId.trim() : UUID.randomUUID().toString();
    }

    private boolean isAdministrator() {
        LoginUser user = SecurityContextHolder.getLoginUser();
        if (user == null) {
            return false;
        }
        return user.isAdmin() || (user.getRoleKeys() != null
                && user.getRoleKeys().stream().anyMatch(ADMIN_ROLES::contains));
    }
}
