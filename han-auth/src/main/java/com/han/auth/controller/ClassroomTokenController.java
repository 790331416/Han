package com.han.auth.controller;

import com.han.auth.sdfz.digitalcampus.ClassroomTokenService;
import com.han.auth.sdfz.digitalcampus.ClassroomTokenVO;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.annotation.RateLimiter;
import com.han.common.security.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 本地账号的三课堂兼容凭证入口。
 *
 * <p>与 {@code /auth/external/digital-campus/classroom-token} 并列存在、互不影响：
 * 那条是数字校园换票，这条只认 Han 自己的登录态，供已经登录 Han 的教师直接进课堂。
 * 该路径不在网关白名单里，必须先有 Han 登录态才能到达。
 */
@RestController
@RequiredArgsConstructor
public class ClassroomTokenController {

    private final ClassroomTokenService classroomTokenService;

    @RateLimiter(key = "classroomLocalToken", time = 60, count = 30,
            limitType = RateLimiter.LimitType.IP)
    @PermissionExempt("已登录用户为自己换取三课堂兼容凭证，身份取自当前登录态而非请求参数")
    @PostMapping("/auth/external/classroom/local-token")
    public R<ClassroomTokenVO> localToken(@RequestParam(value = "identityId", required = false) String identityId) {
        return R.ok(classroomTokenService.exchangeLocal(SecurityContextHolder.getLoginUser(), identityId));
    }

    @GetMapping("/auth/external/classroom/identities")
    @PermissionExempt("当前登录用户查看本人教育身份，由网关 Token 校验和服务端归属校验控制")
    public R<List<com.han.api.system.domain.ClassroomIdentityVO>> identities() {
        return R.ok(classroomTokenService.listLocalIdentities(SecurityContextHolder.getLoginUser()));
    }
}
