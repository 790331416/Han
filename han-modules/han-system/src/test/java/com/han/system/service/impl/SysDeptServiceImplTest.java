package com.han.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.han.common.core.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import com.han.system.converter.SysDeptConverter;
import com.han.system.domain.dto.SysDeptDto;
import com.han.system.domain.po.SysDeptPo;
import com.han.system.domain.query.SysDeptQuery;
import com.han.system.mapper.SysDeptMapper;
import com.han.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 部门树四连缺陷（工单 S-38）的回归测试。
 */
class SysDeptServiceImplTest {

    private SysDeptMapper deptMapper;
    private SysDeptServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 单测里没有 MyBatis 容器，手动注册实体元数据，才能把 LambdaQueryWrapper 渲染成 SQL 片段
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, GlobalConfigUtils.defaults());
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SysDeptPo.class);
    }

    @BeforeEach
    void setUp() {
        deptMapper = mock(SysDeptMapper.class);
        service = new SysDeptServiceImpl(deptMapper, mock(SysUserMapper.class), mock(SysDeptConverter.class));
        when(deptMapper.selectCount(any())).thenReturn(0L);
    }

    private static SysDeptPo dept(long id, long parentId, String ancestors, String name) {
        SysDeptPo po = new SysDeptPo();
        po.setId(id);
        po.setParentId(parentId);
        po.setAncestors(ancestors);
        po.setDeptName(name);
        po.setOrderNum(1);
        return po;
    }

    @Test
    @DisplayName("移动到自己的子孙下必须被拦下，否则整棵树成环")
    void rejectMovingIntoOwnSubtree() {
        when(deptMapper.selectById(100L)).thenReturn(dept(100L, 0L, "0", "总部"));
        when(deptMapper.selectById(1001L)).thenReturn(dept(1001L, 100L, "0,100", "研发中心"));

        SysDeptDto dto = SysDeptDto.builder().deptId(100L).parentId(1001L).deptName("总部").build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateDept(dto));
        assertEquals("上级部门不能是自己的下级部门", ex.getMessage());
    }

    @Test
    @DisplayName("移动到自己下面也要拦下")
    void rejectMovingUnderItself() {
        when(deptMapper.selectById(100L)).thenReturn(dept(100L, 0L, "0", "总部"));

        SysDeptDto dto = SysDeptDto.builder().deptId(100L).parentId(100L).deptName("总部").build();

        assertThrows(BusinessException.class, () -> service.updateDept(dto));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("级联更新 ancestors 带分隔符边界，不会误伤 ID 前缀相同的兄弟子树")
    void cascadeUsesSeparatorBoundary() {
        when(deptMapper.selectById(100L)).thenReturn(dept(100L, 0L, "0", "总部"));
        when(deptMapper.selectById(200L)).thenReturn(dept(200L, 0L, "0", "分部"));
        SysDeptPo child = dept(101L, 100L, "0,100", "子部门");
        when(deptMapper.selectList(any())).thenReturn(List.of(child));

        service.updateDept(SysDeptDto.builder().deptId(100L).parentId(200L).deptName("总部").build());

        ArgumentCaptor<LambdaQueryWrapper<SysDeptPo>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(deptMapper).selectList(captor.capture());
        LambdaQueryWrapper<SysDeptPo> wrapper = captor.getValue();
        // MyBatis-Plus 的占位参数是懒填充的，取 sqlSegment 才会把值写进 paramNameValuePairs
        wrapper.getSqlSegment();
        List<Object> values = List.copyOf(wrapper.getParamNameValuePairs().values());

        // 命中条件必须是「恰好等于 0,100」或「以 0,100, 开头」——绝不能是裸前缀 0,100%
        assertTrue(values.contains("0,100"), "应包含精确匹配值 0,100，实际: " + values);
        assertTrue(values.contains("0,100,%"), "应包含带分隔符的前缀 0,100,%，实际: " + values);
        assertTrue(values.stream().noneMatch("0,100%"::equals), "不允许出现无边界前缀 0,100%");

        assertEquals("0,200,100", child.getAncestors());
    }

    @Test
    @DisplayName("按名称搜索命中深层节点时，祖先要被补回来而不是整支丢弃")
    void searchKeepsDeepMatchesByAppendingAncestors() {
        when(deptMapper.selectDeptListWithLeader("研发一组", null))
                .thenReturn(List.of(dept(1002L, 1001L, "0,100,1001", "研发一组")));
        when(deptMapper.selectList(any()))
                .thenReturn(List.of(dept(100L, 0L, "0", "总部"), dept(1001L, 100L, "0,100", "研发中心")));

        SysDeptQuery query = new SysDeptQuery();
        query.setDeptName("研发一组");

        List<SysDeptPo> roots = service.selectDeptTree(query);

        assertEquals(1, roots.size());
        assertEquals(100L, roots.get(0).getId());
        assertEquals(1001L, roots.get(0).getChildren().get(0).getId());
        assertEquals(1002L, roots.get(0).getChildren().get(0).getChildren().get(0).getId());
    }

    @Test
    @DisplayName("详情要回填 sort，否则编辑弹窗一保存就把排序清零")
    void detailFillsSortFromOrderNum() {
        SysDeptPo po = dept(100L, 0L, "0", "总部");
        po.setOrderNum(7);
        when(deptMapper.selectById(100L)).thenReturn(po);

        assertEquals(7, service.selectDeptById(100L).getSort());
    }
}
