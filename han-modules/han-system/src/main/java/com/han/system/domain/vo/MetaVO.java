package com.han.system.domain.vo;

import lombok.Builder;

/**
 * 路由元信息（只读值对象）
 */
@Builder
public record MetaVO(
        /** 菜单标题 */
        String title,
        /** 菜单图标 */
        String icon,
        /** 是否不缓存 */
        Boolean noCache,
        /** 外链地址 */
        String link
) {
}
