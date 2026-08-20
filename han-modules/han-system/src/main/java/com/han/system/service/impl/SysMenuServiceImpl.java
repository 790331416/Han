package com.han.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.exception.BusinessException;
import com.han.system.domain.po.SysMenuPo;
import com.han.system.domain.po.SysRoleMenuPo;
import com.han.system.domain.po.SysUserRolePo;
import com.han.system.domain.vo.MetaVO;
import com.han.system.domain.vo.RouterVO;
import com.han.system.mapper.SysMenuMapper;
import com.han.system.mapper.SysRoleMenuMapper;
import com.han.system.mapper.SysUserRoleMapper;
import com.han.system.service.ISysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单服务实现
 */
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl implements ISysMenuService {

    /** 菜单类型：目录 */
    private static final String TYPE_DIR = "M";
    /** 菜单类型：菜单 */
    private static final String TYPE_MENU = "C";
    /** 菜单类型：按钮 */
    private static final String TYPE_BUTTON = "F";
    /** Layout 组件 */
    private static final String LAYOUT = "Layout";
    /** ParentView 组件 */
    private static final String PARENT_VIEW = "ParentView";
    /** InnerLink 组件 */
    private static final String INNER_LINK = "InnerLink";

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public List<SysMenuPo> selectMenuList(String menuName, Integer status) {
        LambdaQueryWrapper<SysMenuPo> wrapper = new LambdaQueryWrapper<SysMenuPo>()
                .like(menuName != null && !menuName.isEmpty(), SysMenuPo::getMenuName, menuName)
                .eq(status != null, SysMenuPo::getStatus, status)
                .orderByAsc(SysMenuPo::getParentId)
                .orderByAsc(SysMenuPo::getSort);
        return menuMapper.selectList(wrapper);
    }

    @Override
    public List<SysMenuPo> selectMenuTreeByUserId(Long userId) {
        List<SysMenuPo> menus;
        // 管理员查全部菜单
        if (userId != null && userId == 1L) {
            menus = menuMapper.selectList(
                    new LambdaQueryWrapper<SysMenuPo>()
                            .in(SysMenuPo::getMenuType, TYPE_DIR, TYPE_MENU)
                            .eq(SysMenuPo::getStatus, 0)
                            .orderByAsc(SysMenuPo::getParentId)
                            .orderByAsc(SysMenuPo::getSort)
            );
        } else {
            // 非管理员：通过 user_role → role_menu 查询
            List<Long> menuIds = selectMenuIdsByUserId(userId);
            if (menuIds.isEmpty()) {
                return List.of();
            }
            // 角色只配置了叶子菜单时，补回其目录祖先用于构造树；祖先本身不增加任何叶子权限。
            List<SysMenuPo> allMenus = menuMapper.selectList(
                    new LambdaQueryWrapper<SysMenuPo>()
                            .in(SysMenuPo::getMenuType, TYPE_DIR, TYPE_MENU)
                            .eq(SysMenuPo::getStatus, 0)
                            .orderByAsc(SysMenuPo::getParentId)
                            .orderByAsc(SysMenuPo::getSort)
            );
            Set<Long> visibleIds = new java.util.LinkedHashSet<>(menuIds);
            boolean changed;
            do {
                changed = false;
                for (SysMenuPo menu : allMenus) {
                    if (visibleIds.contains(menu.getId()) && menu.getParentId() != null && menu.getParentId() != 0L) {
                        changed |= visibleIds.add(menu.getParentId());
                    }
                }
            } while (changed);
            menus = allMenus.stream().filter(menu -> visibleIds.contains(menu.getId())).toList();
        }
        return buildTree(menus);
    }

    @Override
    public List<RouterVO> buildRouters(List<SysMenuPo> menus) {
        List<RouterVO> routers = new LinkedList<>();
        for (SysMenuPo menu : menus) {
            RouterVO.RouterVOBuilder builder = RouterVO.builder()
                    .hidden(menu.getVisible() != null && menu.getVisible() == 1)
                    .name(getRouteName(menu))
                    .path(getRouterPath(menu))
                    .component(getComponent(menu))
                    .query(menu.getQuery())
                    .meta(MetaVO.builder()
                            .title(menu.getMenuName())
                            .icon(menu.getIcon())
                            .noCache(menu.getIsCache() != null && menu.getIsCache() == 1)
                            .link(isHttp(menu.getPath()) ? menu.getPath() : null)
                            .build());

            List<SysMenuPo> children = menu.getChildren();
            if (children != null && !children.isEmpty() && TYPE_DIR.equals(menu.getMenuType())) {
                builder.alwaysShow(true)
                        .redirect("noRedirect")
                        .children(buildRouters(children));
            } else if (isMenuFrame(menu)) {
                // 顶级菜单且非外链 → 包裹成 Layout 的子节点
                RouterVO childRouter = RouterVO.builder()
                        .path(menu.getPath())
                        .component(menu.getComponent())
                        .name(capitalize(menu.getPath()))
                        .meta(MetaVO.builder()
                                .title(menu.getMenuName())
                                .icon(menu.getIcon())
                                .noCache(menu.getIsCache() != null && menu.getIsCache() == 1)
                                .link(isHttp(menu.getPath()) ? menu.getPath() : null)
                                .build())
                        .query(menu.getQuery())
                        .build();
                builder.meta(null).children(List.of(childRouter));
            } else if (menu.getParentId() == 0L && isInnerLink(menu)) {
                // 顶级内链
                RouterVO childRouter = RouterVO.builder()
                        .path(innerLinkReplaceEach(menu.getPath()))
                        .component(INNER_LINK)
                        .name(capitalize(innerLinkReplaceEach(menu.getPath())))
                        .meta(MetaVO.builder()
                                .title(menu.getMenuName())
                                .icon(menu.getIcon())
                                .link(menu.getPath())
                                .build())
                        .build();
                builder.meta(MetaVO.builder().title(menu.getMenuName()).icon(menu.getIcon()).build())
                        .path("/")
                        .children(List.of(childRouter));
            }
            routers.add(builder.build());
        }
        return routers;
    }

    @Override
    public List<SysMenuPo> selectMenuTree() {
        List<SysMenuPo> menus = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenuPo>()
                        .eq(SysMenuPo::getStatus, 0)
                        .orderByAsc(SysMenuPo::getParentId)
                        .orderByAsc(SysMenuPo::getSort)
        );
        return buildTree(menus);
    }

    @Override
    public List<Long> selectMenuListByRoleId(Long roleId) {
        List<SysRoleMenuPo> roleMenus = roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenuPo>().eq(SysRoleMenuPo::getRoleId, roleId)
        );
        return roleMenus.stream().map(SysRoleMenuPo::getMenuId).toList();
    }

    @Override
    public Set<String> selectMenuPermsByUserId(Long userId) {
        List<Long> menuIds = selectMenuIdsByUserId(userId);
        if (menuIds.isEmpty()) {
            return Set.of();
        }
        List<SysMenuPo> menus = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenuPo>()
                        .in(SysMenuPo::getId, menuIds)
                        .eq(SysMenuPo::getStatus, 0)
        );
        return menus.stream()
                .map(SysMenuPo::getPerms)
                .filter(p -> p != null && !p.isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    public SysMenuPo selectMenuById(Long menuId) {
        return menuMapper.selectById(menuId);
    }

    @Override
    public boolean hasChildByMenuId(Long menuId) {
        return menuMapper.selectCount(
                new LambdaQueryWrapper<SysMenuPo>().eq(SysMenuPo::getParentId, menuId)
        ) > 0;
    }

    @Override
    public boolean checkMenuExistRole(Long menuId) {
        return roleMenuMapper.selectCount(
                new LambdaQueryWrapper<SysRoleMenuPo>().eq(SysRoleMenuPo::getMenuId, menuId)
        ) > 0;
    }

    @Override
    public boolean checkMenuNameUnique(String menuName, Long parentId, Long menuId) {
        LambdaQueryWrapper<SysMenuPo> wrapper = new LambdaQueryWrapper<SysMenuPo>()
                .eq(SysMenuPo::getMenuName, menuName)
                .eq(SysMenuPo::getParentId, parentId != null ? parentId : 0L);
        if (menuId != null) {
            wrapper.ne(SysMenuPo::getId, menuId);
        }
        return menuMapper.selectCount(wrapper) == 0;
    }

    @Override
    public void insertMenu(SysMenuPo menu) {
        if (!checkMenuNameUnique(menu.getMenuName(), menu.getParentId(), null)) {
            throw new BusinessException("菜单名称[" + menu.getMenuName() + "]已存在");
        }
        menuMapper.insert(menu);
    }

    @Override
    public void updateMenu(SysMenuPo menu) {
        if (!checkMenuNameUnique(menu.getMenuName(), menu.getParentId(), menu.getId())) {
            throw new BusinessException("菜单名称[" + menu.getMenuName() + "]已存在");
        }
        menuMapper.updateById(menu);
    }

    @Override
    public void deleteMenuById(Long menuId) {
        if (hasChildByMenuId(menuId)) {
            throw new BusinessException("存在子菜单，不允许删除");
        }
        if (checkMenuExistRole(menuId)) {
            throw new BusinessException("菜单已分配角色，不允许删除");
        }
        menuMapper.deleteById(menuId);
    }

    // ==================== 私有方法 ====================

    /**
     * 通过 user_role + role_menu 查询用户的菜单ID列表
     */
    private List<Long> selectMenuIdsByUserId(Long userId) {
        List<SysUserRolePo> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRolePo>().eq(SysUserRolePo::getUserId, userId)
        );
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRolePo::getRoleId).toList();
        List<SysRoleMenuPo> roleMenus = roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenuPo>().in(SysRoleMenuPo::getRoleId, roleIds)
        );
        return roleMenus.stream().map(SysRoleMenuPo::getMenuId).distinct().toList();
    }

    private List<SysMenuPo> buildTree(List<SysMenuPo> menus) {
        Map<Long, List<SysMenuPo>> grouped = menus.stream()
                .collect(Collectors.groupingBy(SysMenuPo::getParentId));
        List<SysMenuPo> roots = new ArrayList<>();
        for (SysMenuPo menu : menus) {
            List<SysMenuPo> children = grouped.getOrDefault(menu.getId(), List.of());
            menu.setChildren(new ArrayList<>(children));
            if (menu.getParentId() == 0L) {
                roots.add(menu);
            }
        }
        return roots;
    }

    /**
     * 获取路由名称
     */
    private String getRouteName(SysMenuPo menu) {
        // 非外链且是顶级目录（menuFrame类型）
        if (isMenuFrame(menu)) {
            return "";
        }
        return capitalize(menu.getPath());
    }

    /**
     * 获取路由地址
     */
    private String getRouterPath(SysMenuPo menu) {
        String path = menu.getPath();
        // 内链打开外网方式
        if (menu.getParentId() != 0L && isInnerLink(menu)) {
            path = innerLinkReplaceEach(path);
        }
        // 非外链且是顶级目录
        if (menu.getParentId() == 0L && TYPE_DIR.equals(menu.getMenuType())
                && (menu.getIsFrame() == null || menu.getIsFrame() == 1)) {
            path = "/" + path;
        }
        // 非外链且是顶级菜单
        if (isMenuFrame(menu)) {
            path = "/";
        }
        return path;
    }

    /**
     * 获取组件路径
     */
    private String getComponent(SysMenuPo menu) {
        String component = LAYOUT;
        if (menu.getComponent() != null && !menu.getComponent().isEmpty() && !isMenuFrame(menu)) {
            component = menu.getComponent();
        } else if (menu.getComponent() == null || menu.getComponent().isEmpty()) {
            if (menu.getParentId() != 0L && isInnerLink(menu)) {
                component = INNER_LINK;
            } else if (menu.getParentId() != 0L && TYPE_DIR.equals(menu.getMenuType())) {
                component = PARENT_VIEW;
            }
        }
        return component;
    }

    /**
     * 是否为菜单内部跳转（顶级菜单且非外链）
     */
    private boolean isMenuFrame(SysMenuPo menu) {
        return menu.getParentId() == 0L
                && TYPE_MENU.equals(menu.getMenuType())
                && (menu.getIsFrame() == null || menu.getIsFrame() == 1);
    }

    /**
     * 是否为内链
     */
    private boolean isInnerLink(SysMenuPo menu) {
        return menu.getIsFrame() != null && menu.getIsFrame() == 0 && isHttp(menu.getPath());
    }

    private boolean isHttp(String link) {
        return link != null && (link.startsWith("http://") || link.startsWith("https://"));
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * 内链地址替换特殊字符
     */
    private String innerLinkReplaceEach(String path) {
        if (path == null) {
            return "";
        }
        return path.replace("http://", "")
                .replace("https://", "")
                .replace("www.", "")
                .replace(".", "/")
                .replace(":", "/");
    }
}
