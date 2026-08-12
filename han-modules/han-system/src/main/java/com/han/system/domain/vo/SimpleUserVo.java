package com.han.system.domain.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 下拉选择用的精简用户信息。
 *
 * <p>该接口对全部已登录用户开放，因此联系方式属于按需下发字段：
 * 只有具备用户查询或部门维护权限的调用方才会拿到 {@code phone} / {@code email}
 * （部门维护页需要用负责人的真实联系方式回填部门联系方式，因此这里不做掩码），
 * 其余调用方只能拿到 {@code userId} 与 {@code nickname}。
 */
@Data
@Builder
public class SimpleUserVo {

    /** 用户ID */
    private Long userId;

    /** 昵称 */
    private String nickname;

    /** 手机号（无联系方式权限时为 null） */
    private String phone;

    /** 邮箱（无联系方式权限时为 null） */
    private String email;
}
