package com.han.open.controller;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.EducationDeviceDirectoryVO;
import com.han.api.system.domain.EducationPersonDirectoryVO;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.annotation.RateLimiter;
import com.han.open.domain.vo.OpenAccessTokenContext;
import com.han.open.service.IOAuth2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 供视频平台等已授权服务端应用读取的教育目录。
 *
 * <p>请求只接受开放平台 OAuth2 Bearer Token；目录数据始终经 han-system 的内部接口读取，
 * 不向第三方暴露内部路由、数据库或人员账号字段。</p>
 */
@RestController
@RequestMapping("/open/api/v1/directory")
@RequiredArgsConstructor
public class OpenDirectoryController {

    private static final String TEACHER_SCOPE = "edu.teacher.read";
    private static final String STUDENT_SCOPE = "edu.student.read";
    private static final String DEVICE_SCOPE = "edu.device.read";
    private static final String TEACHER_RESOURCE = "directory.teachers.read";
    private static final String STUDENT_RESOURCE = "directory.students.read";
    private static final String DEVICE_RESOURCE = "directory.devices.read";

    private final IOAuth2Service oauth2Service;
    private final SystemServiceClient systemServiceClient;

    @GetMapping("/teachers")
    @RateLimiter(key = "openDirectory", time = 60, count = 300, limitType = RateLimiter.LimitType.IP)
    public R<PageResult<EducationPersonDirectoryVO>> teachers(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedAfter,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        OpenAccessTokenContext context = context(authorization, TEACHER_SCOPE, TEACHER_RESOURCE);
        return systemServiceClient.listOpenDirectoryPeople(context.tenantId(), schools(context, schoolId), "TEACHER",
                status, updatedAfter, pageNum, pageSize);
    }

    @GetMapping("/students")
    @RateLimiter(key = "openDirectory", time = 60, count = 300, limitType = RateLimiter.LimitType.IP)
    public R<PageResult<EducationPersonDirectoryVO>> students(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedAfter,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        OpenAccessTokenContext context = context(authorization, STUDENT_SCOPE, STUDENT_RESOURCE);
        return systemServiceClient.listOpenDirectoryPeople(context.tenantId(), schools(context, schoolId), "STUDENT",
                status, updatedAfter, pageNum, pageSize);
    }

    @GetMapping("/devices")
    @RateLimiter(key = "openDirectory", time = 60, count = 300, limitType = RateLimiter.LimitType.IP)
    public R<PageResult<EducationDeviceDirectoryVO>> devices(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedAfter,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        OpenAccessTokenContext context = context(authorization, DEVICE_SCOPE, DEVICE_RESOURCE);
        return systemServiceClient.listOpenDirectoryDevices(context.tenantId(), schools(context, schoolId),
                status, updatedAfter, pageNum, pageSize);
    }

    private OpenAccessTokenContext context(String authorization, String scope, String resourceCode) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException("缺少开放平台 Bearer Token");
        }
        return oauth2Service.requireAccessToken(authorization.substring("Bearer ".length()).trim(), scope, resourceCode);
    }

    private List<Long> schools(OpenAccessTokenContext context, Long schoolId) {
        List<Long> granted = context.schoolIds() == null ? List.of() : context.schoolIds();
        if (schoolId == null) {
            return granted;
        }
        if (!granted.contains(schoolId)) {
            throw new BusinessException("应用未获该学校的数据授权");
        }
        return List.of(schoolId);
    }
}
