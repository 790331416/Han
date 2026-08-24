package com.han.api.system;

import com.han.api.system.domain.SessionRevokeRequest;
import com.han.common.core.domain.R;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * han-auth 内部会话撤销接口（@HttpExchange 声明式客户端）。
 *
 * <p>接口名遵循 {@code XxxServiceClient -> han-xxx} 的服务名推导约定，解析为
 * {@code han-auth}；会话撤销契约：{@code POST /inner/auth/session/revoke}。</p>
 *
 * <p>撤销语义：仅带 {@code userId} 时撤销该账号的全部会话；同时带 {@code identityId}
 * 时只撤销该教育身份对应会话与课堂 token。</p>
 */
@HttpExchange("/inner/auth")
public interface AuthServiceClient {

    /**
     * 撤销用户会话。
     *
     * @param request {@code userId} 必填；{@code identityId} 可选，按身份粒度撤销。
     */
    @PostExchange("/session/revoke")
    R<Void> revokeSession(@RequestBody SessionRevokeRequest request);
}
