package com.han.open.controller;

import com.han.common.core.exception.BusinessException;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.RateLimiter;
import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.EducationDeviceDirectoryVO;
import com.han.open.domain.vo.OpenAccessTokenContext;
import com.han.open.service.IOAuth2Service;
import com.han.open.service.OpenClassroomProxyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 视频平台兼容接口。
 *
 * <p>业务参数和下游 Result 信封与数字校园时代保持一致；认证改为开放平台 Bearer Token。</p>
 */
@RestController
@RequestMapping("/open/api/v1/classroom")
@RequiredArgsConstructor
public class OpenClassroomController {

    private final IOAuth2Service oauth2Service;
    private final OpenClassroomProxyService proxyService;
    private final SystemServiceClient systemServiceClient;
    private final ObjectMapper objectMapper;

    @GetMapping("/course/deliveryClassroom/getLiveStatusByUUID")
    @RateLimiter(key = "openClassroom", time = 60, count = 120, limitType = RateLimiter.LimitType.IP)
    public ResponseEntity<String> liveStatus(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             HttpServletRequest request) {
        return forward(HttpMethod.GET, "/inner/open-classroom/live/getLiveStatusByUUID", authorization,
                "classroom.live.read", "classroom.live-status.read", request, null);
    }

    @GetMapping("/user/tAppUpgrade/getAppUpgradeInfo")
    @RateLimiter(key = "openClassroom", time = 60, count = 120, limitType = RateLimiter.LimitType.IP)
    public ResponseEntity<String> appUpgrade(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             HttpServletRequest request) {
        return forward(HttpMethod.GET, "/inner/open-classroom/user/tAppUpgrade/getAppUpgradeInfo", authorization,
                "classroom.app.read", "classroom.app-upgrade.read", request, null);
    }

    @PostMapping("/tb-course-info/getCourseInfoList")
    @RateLimiter(key = "openClassroom", time = 60, count = 120, limitType = RateLimiter.LimitType.IP)
    public ResponseEntity<String> courseList(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             HttpServletRequest request) {
        OpenAccessTokenContext context = context(authorization, "classroom.course.read", "classroom.course.list");
        return proxyService.forward(HttpMethod.POST, "/inner/open-classroom/tb-course-info/getCourseInfoList",
                params(request), null, selectedSchool(context, request.getParameter("organId")));
    }

    @PostMapping("/tb-course-info/saveCourseInfo")
    @RateLimiter(key = "openClassroomControl", time = 60, count = 30, limitType = RateLimiter.LimitType.IP)
    public ResponseEntity<String> saveCourse(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             HttpServletRequest request, @RequestBody String body) {
        return forward(HttpMethod.POST, "/inner/open-classroom/tb-course-info/saveCourseInfo", authorization,
                "classroom.course.write", "classroom.course.save", request, body);
    }

    @PostMapping("/live/startClassroom")
    @RateLimiter(key = "openClassroomControl", time = 60, count = 30, limitType = RateLimiter.LimitType.IP)
    public ResponseEntity<String> startClassroom(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                  HttpServletRequest request) {
        return forward(HttpMethod.POST, "/inner/open-classroom/live/startClassroom", authorization,
                "classroom.live.control", "classroom.live.start", request, null);
    }

    @PostMapping("/live/joinClassroom")
    @RateLimiter(key = "openClassroomControl", time = 60, count = 30, limitType = RateLimiter.LimitType.IP)
    public ResponseEntity<String> joinClassroom(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                 HttpServletRequest request) {
        return forward(HttpMethod.POST, "/inner/open-classroom/live/joinClassroom", authorization,
                "classroom.live.control", "classroom.live.join", request, null);
    }

    @PostMapping("/live/enterCourse")
    @RateLimiter(key = "openClassroomControl", time = 60, count = 30, limitType = RateLimiter.LimitType.IP)
    public ResponseEntity<String> enterCourse(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               HttpServletRequest request) {
        return forward(HttpMethod.POST, "/inner/open-classroom/live/enterCourse", authorization,
                "classroom.live.control", "classroom.live.enter", request, null);
    }

    @PostMapping("/live/StartRecord")
    @RateLimiter(key = "openClassroomControl", time = 60, count = 30, limitType = RateLimiter.LimitType.IP)
    public ResponseEntity<String> startRecord(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               HttpServletRequest request) {
        return forward(HttpMethod.POST, "/inner/open-classroom/live/StartRecord", authorization,
                "classroom.record.control", "classroom.record.start", request, null);
    }

    @PostMapping("/live/StopRecordByUUID")
    @RateLimiter(key = "openClassroomControl", time = 60, count = 30, limitType = RateLimiter.LimitType.IP)
    public ResponseEntity<String> stopRecord(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              HttpServletRequest request) {
        return forward(HttpMethod.POST, "/inner/open-classroom/live/StopRecordByUUID", authorization,
                "classroom.record.control", "classroom.record.stop", request, null);
    }

    @PostMapping("/live/kickPeople")
    @RateLimiter(key = "openClassroomControl", time = 60, count = 30, limitType = RateLimiter.LimitType.IP)
    public ResponseEntity<String> kickPeople(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             HttpServletRequest request) {
        return forward(HttpMethod.POST, "/inner/open-classroom/live/kickPeople", authorization,
                "classroom.member.control", "classroom.member.kick", request, null);
    }

    @PostMapping("/live/muteMember")
    @RateLimiter(key = "openClassroomControl", time = 60, count = 30, limitType = RateLimiter.LimitType.IP)
    public ResponseEntity<String> muteMember(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             HttpServletRequest request) {
        return forward(HttpMethod.POST, "/inner/open-classroom/live/muteMember", authorization,
                "classroom.member.control", "classroom.member.mute", request, null);
    }

    @GetMapping("/common/getDeviceInfoByDeviceCode")
    @RateLimiter(key = "openClassroom", time = 60, count = 120, limitType = RateLimiter.LimitType.IP)
    public ResponseEntity<String> device(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         HttpServletRequest request) {
        OpenAccessTokenContext context = context(authorization, "classroom.device.read", "classroom.device.read");
        String deviceCode = request.getParameter("deviceCode");
        R<EducationDeviceDirectoryVO> response = systemServiceClient.getOpenDirectoryDevice(
                context.tenantId(), context.schoolIds(), deviceCode);
        if (response == null || response.getCode() != 200) {
            throw new BusinessException(response == null ? "设备目录服务不可用" : response.getMsg());
        }
        return legacyDevice(response.getData());
    }

    @GetMapping("/event/eventSubscriptions")
    @RateLimiter(key = "openClassroom", time = 60, count = 120, limitType = RateLimiter.LimitType.IP)
    public ResponseEntity<String> eventSubscriptions(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      HttpServletRequest request) {
        return forward(HttpMethod.GET, "/inner/open-classroom/event/eventSubscriptions", authorization,
                "classroom.event.read", "classroom.event.subscribe", request, null);
    }

    @PostMapping("/event/addClassOverEvent")
    @RateLimiter(key = "openClassroomControl", time = 60, count = 30, limitType = RateLimiter.LimitType.IP)
    public ResponseEntity<String> addClassOverEvent(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                     HttpServletRequest request) {
        return forward(HttpMethod.POST, "/inner/open-classroom/event/addClassOverEvent", authorization,
                "classroom.live.control", "classroom.event.class-over", request, null);
    }

    private ResponseEntity<String> forward(HttpMethod method, String path, String authorization,
                                           String scope, String resourceCode,
                                           HttpServletRequest request, String body) {
        OpenAccessTokenContext context = context(authorization, scope, resourceCode);
        return proxyService.forward(method, path, params(request), body, context);
    }

    private OpenAccessTokenContext context(String authorization, String scope, String resourceCode) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException("缺少开放平台 Bearer Token");
        }
        return oauth2Service.requireAccessToken(authorization.substring("Bearer ".length()).trim(), scope, resourceCode);
    }

    private static OpenAccessTokenContext selectedSchool(OpenAccessTokenContext context, String organId) {
        if (organId == null || organId.isBlank()) {
            if (context.schoolIds().size() != 1) {
                throw new BusinessException("多学校应用查询课程时必须传 organId");
            }
            return context;
        }
        final long schoolId;
        try {
            schoolId = Long.parseLong(organId.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException("organId 格式不正确");
        }
        if (!context.schoolIds().contains(schoolId)) {
            throw new BusinessException("应用未获该学校的数据授权");
        }
        return new OpenAccessTokenContext(context.userId(), context.tenantId(), context.clientId(), context.scopes(),
                List.of(schoolId), context.applicationVersion(), context.refreshToken(), context.appId(), context.environment());
    }

    private static MultiValueMap<String, String> params(HttpServletRequest request) {
        MultiValueMap<String, String> result = new LinkedMultiValueMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            for (String value : entry.getValue()) {
                result.add(entry.getKey(), value);
            }
        }
        return result;
    }

    private ResponseEntity<String> legacyDevice(EducationDeviceDirectoryVO device) {
        if (device == null) {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("success", false);
            envelope.put("message", "设备不存在或不在授权学校范围");
            envelope.put("code", 500);
            envelope.put("result", null);
            envelope.put("timestamp", System.currentTimeMillis());
            return legacyJson(envelope);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pk_id", String.valueOf(device.deviceId()));
        result.put("device_name", device.deviceName());
        result.put("device_code", device.deviceCode());
        result.put("device_type", device.deviceType());
        result.put("device_type_name", device.deviceType());
        if (device.status() != null) {
            result.put("device_status", device.status());
            result.put("state", String.valueOf(device.status()));
        }
        if (device.schoolId() != null) {
            result.put("org_id", String.valueOf(device.schoolId()));
            result.put("org_name", device.schoolName());
        }
        if (device.roomId() != null) {
            result.put("place_id", String.valueOf(device.roomId()));
            result.put("place_name", device.roomName());
        }
        if (device.applicationTypes() != null && !device.applicationTypes().isEmpty()) {
            String applications = String.join(",", device.applicationTypes());
            result.put("application_type", applications);
            result.put("application_type_name", applications);
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("success", true);
        envelope.put("message", "操作成功！");
        envelope.put("code", 200);
        envelope.put("result", result);
        envelope.put("timestamp", System.currentTimeMillis());
        return legacyJson(envelope);
    }

    private ResponseEntity<String> legacyJson(Map<String, Object> envelope) {
        try {
            return ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("设备响应序列化失败", ex);
        }
    }
}
