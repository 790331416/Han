package com.xuman.system.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 菜单响应VO
 */
@Data
public class MenuVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 菜单ID
     */
    private Long menuId;

    /**
     * 父菜单ID
     */
    private Long parentId;

    /**
     * 菜单名称
     */
    private String menuName;

    /**
     * 菜单类型(M目录 C菜单 F按钮)
     */
    private String menuType;

    /**
     * 路由地址
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 路由参数
     */
    private String query;

    /**
     * 权限标识
     */
    private String perms;

    /**
     * 菜单图标
     */
    private String icon;

    /**
     * 显示顺序
     */
    private Integer sort;

    /**
     * 显示状态
     */
    private Integer visible;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 是否外链
     */
    private Integer isFrame;

    /**
     * 是否缓存
     */
    private Integer isCache;

    /**
     * 子菜单
     */
    private List<MenuVO> children;
}
