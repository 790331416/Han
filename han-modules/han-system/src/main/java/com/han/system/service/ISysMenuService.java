package com.han.system.service;

import com.han.system.domain.po.SysMenuPo;
import com.han.system.domain.vo.RouterVO;

import java.util.List;
import java.util.Set;

/**
 * 菜单服务接口
 */
public interface ISysMenuService {

    /**
     * 查询菜单列表
     */
    List<SysMenuPo> selectMenuList(String menuName, Integer status);

    /**
     * 根据用户ID查询菜单树（过滤按钮）
     */
    List<SysMenuPo> selectMenuTreeByUserId(Long userId);

    /**
     * 构建前端路由
     */
    List<RouterVO> buildRouters(List<SysMenuPo> menus);

    /**
     * 查询全量菜单树（角色分配用）
     */
    List<SysMenuPo> selectMenuTree();

    /**
     * 查询角色已选菜单ID列表
     */
    List<Long> selectMenuListByRoleId(Long roleId);

    /**
     * 查询用户权限标识集合
     */
    Set<String> selectMenuPermsByUserId(Long userId);

    /**
     * 根据ID查询菜单
     */
    SysMenuPo selectMenuById(Long menuId);

    /**
     * 是否存在子菜单
     */
    boolean hasChildByMenuId(Long menuId);

    /**
     * 菜单是否已分配给角色
     */
    boolean checkMenuExistRole(Long menuId);

    /**
     * 校验菜单名称唯一
     */
    boolean checkMenuNameUnique(String menuName, Long parentId, Long menuId);

    /**
     * 新增菜单
     */
    void insertMenu(SysMenuPo menu);

    /**
     * 修改菜单
     */
    void updateMenu(SysMenuPo menu);

    /**
     * 删除菜单
     */
    void deleteMenuById(Long menuId);
}
