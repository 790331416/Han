package com.han.api.system.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 同一用户名在各租户下的账号条目。
 *
 * <p>替代原先的 {@code R<List<Map<String, Object>>>} 弱类型契约：字段名和类型全靠约定，
 * 而服务端全局把 {@code Long} 序列化成字符串，{@code Map<String, Object>} 又没有目标类型可供
 * 转换，客户端拿到的 {@code tenantId} 实际是 {@code String}。现在调用方靠手工
 * {@code toLong(...)} 兜住了，换个调用方直接强转就是 ClassCastException。
 * 声明成具体类型后，Jackson 按字段类型反序列化，String 会被正确还原成 Long。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTenantVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 租户ID */
    private Long tenantId;

    /** 账号状态（0 正常） */
    private Integer status;
}
