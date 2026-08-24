package com.han.auth.controller.inner;

import com.han.api.system.domain.SessionRevokeRequest;
import com.han.auth.service.IAuthService;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.InnerAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * han-auth 内部会话撤销接口。
 *
 * <p>契约：{@code POST /inner/auth/session/revoke}，body 为 han-api 的
 * {@link SessionRevokeRequest}。仅带 {@code userId} 时撤销该账号全部会话与课堂凭证；
 * 同时带 {@code identityId} 时只撤销该教育身份对应的会话与课堂 token。
 */
@InnerAuth
@RestController("innerAuthSessionController")
@RequestMapping("/inner/auth")
@RequiredArgsConstructor
public class IAuthSessionController {

    private final IAuthService authService;

    @PostMapping("/session/revoke")
    public R<Void> revokeSession(@RequestBody SessionRevokeRequest request) {
        if (request == null || request.getUserId() == null) {
            return R.fail("用户ID不能为空");
        }
        authService.revokeSession(request.getUserId(), request.getIdentityId());
        return R.ok();
    }
}
