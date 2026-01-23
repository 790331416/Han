package com.xuman.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuman.system.domain.entity.Menu;
import com.xuman.system.domain.vo.MenuVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 菜单Mapper接口
 */
@Mapper
public interface MenuMapper extends BaseMapper<Menu> {

    /**
     * 查询菜单树
     */
    List<MenuVO> selectMenuTree(@Param("tenantId") Long tenantId);

    /**
     * 根据用户ID查询菜单权限
     */
    Set<String> selectPermsByUserId(@Param("userId") Long userId);

    /**
     * 根据角色ID查询菜单
     */
    List<MenuVO> selectMenusByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询用户菜单树
     */
    List<MenuVO> selectMenuTreeByUserId(@Param("userId") Long userId);
}
