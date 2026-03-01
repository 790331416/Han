package com.han.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.List;

/**
 * 前端路由配置（只读值对象）
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RouterVO(
        /** 路由名称 */
        String name,
        /** 路由地址 */
        String path,
        /** 是否隐藏 */
        Boolean hidden,
        /** 重定向地址 */
        String redirect,
        /** 组件路径 */
        String component,
        /** 路由参数 */
        String query,
        /** 总是显示（当有子路由时） */
        Boolean alwaysShow,
        /** 路由元信息 */
        MetaVO meta,
        /** 子路由 */
        List<RouterVO> children
) {
}
