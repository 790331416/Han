package com.han.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.TreeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class Menu extends TreeEntity<Menu> {

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
     * 显示状态(0显示 1隐藏)
     */
    private Integer visible;

    /**
     * 状态(0正常 1停用)
     */
    private Integer status;

    /**
     * 是否为外链(0是 1否)
     */
    private Integer isFrame;

    /**
     * 是否缓存(0缓存 1不缓存)
     */
    private Integer isCache;
}
