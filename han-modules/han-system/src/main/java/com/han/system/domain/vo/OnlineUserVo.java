package com.han.system.domain.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 在线会话视图对象。
 *
 * <p>字段与顺序与此前直接拼的 {@code LinkedHashMap} 保持一致，响应结构不变。
 */
@Data
@Builder
public class OnlineUserVo {

    /** 会话标识（access token） */
    private String tokenId;

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 登录IP */
    private String ipAddr;

    /** 客户端类型 */
    private String clientType;

    /** 登录时间（epoch millis） */
    private Long loginTime;
}
