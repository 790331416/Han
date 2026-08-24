package com.han.api.system.domain;

import lombok.Data;

/**
 * 会话撤销请求（han-auth 内部接口）。
 *
 * <p>{@code userId} 必填；{@code identityId} 可选——省略时撤销该账号全部会话与课堂凭证，
 * 指定时只撤销该教育身份对应的会话与课堂 token。</p>
 */
@Data
public class SessionRevokeRequest {

    /** 用户ID（必填） */
    private Long userId;

    /** 教育身份ID（可选，按身份粒度撤销） */
    private Long identityId;
}
