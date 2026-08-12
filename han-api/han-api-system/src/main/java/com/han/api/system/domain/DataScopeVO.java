package com.han.api.system.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 用户数据权限范围。
 *
 * <p>用来消除 {@code R<Set<Long>> getDataScopeDeptIds} 里 {@code null} 的双关语义。原契约用
 * {@code null} 同时表达「不限制部门范围」和「远程调用失败拿不到值」两件事，而调用方
 * （han-auth）只判 {@code R} 对象本身是否为 null、不看 {@code code}，于是 han-system 一旦返回
 * {@code R.fail}（HTTP 仍是 200、data 为 null），普通用户就会被当成「不限制」写进
 * {@code LoginUser.deptIds}，登录后拿到全部部门的数据权限 —— 一次瞬时故障即提权。
 *
 * <p>拆成两个字段后，「不限制」必须由 {@code unlimited = true} 显式表达，拿不到数据时
 * 默认值就是最严格的那一档（{@code unlimited = false} + 空集合），天然 fail-closed。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataScopeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否不限制部门范围（管理员或 {@code data_scope = 1} 全部数据权限）。
     *
     * <p>基本类型，反序列化拿不到该字段时默认 {@code false}，即「受限」。
     */
    private boolean unlimited;

    /**
     * 允许访问的部门ID集合。
     *
     * <p>{@code unlimited = true} 时本字段无意义；{@code unlimited = false} 且集合为空表示
     * 「仅本人」模式。调用方遇到 {@code null} 一律按空集合处理，不得反向解读成不限制。
     */
    private Set<Long> deptIds;
}
