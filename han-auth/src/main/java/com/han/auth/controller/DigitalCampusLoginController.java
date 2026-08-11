package com.han.auth.controller;

import com.han.auth.sdfz.digitalcampus.DigitalCampusLoginRequest;
import com.han.auth.sdfz.digitalcampus.DigitalCampusLoginService;
import com.han.auth.sdfz.digitalcampus.DigitalCampusLoginVO;
import com.han.auth.sdfz.digitalcampus.ClassroomTokenService;
import com.han.auth.sdfz.digitalcampus.ClassroomTokenVO;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数字校园外部身份登录入口。
 */
@RestController
@RequiredArgsConstructor
public class DigitalCampusLoginController {

    private final DigitalCampusLoginService loginService;
    private final ClassroomTokenService classroomTokenService;

    @RateLimiter(key = "digitalCampusLogin", time = 60, count = 10,
            limitType = RateLimiter.LimitType.IP)
    @PermissionExempt("数字校园登录前公开入口，方法内完成上游 Token 校验和本地身份映射")
    @PostMapping("/auth/external/digital-campus")
    public R<DigitalCampusLoginVO> login(
            @RequestHeader("access-token") String token,
            @RequestBody(required = false) DigitalCampusLoginRequest request) {
        String identityId = request != null ? request.identityId() : null;
        return R.ok(loginService.login(token, identityId));
    }

    @RateLimiter(key = "digitalCampusClassroomToken", time = 60, count = 30,
            limitType = RateLimiter.LimitType.IP)
    @PermissionExempt("数字校园 Token 校验后签发短时三个课堂内部令牌")
    @PostMapping("/auth/external/digital-campus/classroom-token")
    public R<ClassroomTokenVO> classroomToken(
            @RequestHeader("access-token") String token,
            @RequestHeader(value = "x-identity-id", required = false) String identityId) {
        return R.ok(classroomTokenService.exchange(token, identityId));
    }
}
