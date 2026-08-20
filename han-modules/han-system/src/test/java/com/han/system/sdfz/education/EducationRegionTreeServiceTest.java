package com.han.system.sdfz.education;

import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.sdfz.education.domain.EduRegionPo;
import com.han.system.sdfz.education.domain.EducationRegionForms;
import com.han.system.sdfz.education.domain.EducationRegionSearchOption;
import com.han.system.sdfz.education.mapper.EduRegionMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduUserScopeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationRegionTreeServiceTest {
    @Mock private EduRegionMapper regionMapper;
    @Mock private EduSchoolMapper schoolMapper;
    @Mock private EduUserScopeMapper userScopeMapper;
    private EducationRegionTreeService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(1L).build());
        service = new EducationRegionTreeService(regionMapper, schoolMapper, userScopeMapper);
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clear(); }

    @Test
    void createsChildWithStableParentPath() {
        when(regionMapper.selectById(10L)).thenReturn(region(10L, null, "重庆市"));
        when(regionMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> { invocation.<EduRegionPo>getArgument(0).setId(11L); return 1; }).when(regionMapper).insert(any(EduRegionPo.class));

        assertThat(service.save(form(null, 10L, "两江新区"))).isEqualTo(11L);
        ArgumentCaptor<EduRegionPo> captor = ArgumentCaptor.forClass(EduRegionPo.class);
        verify(regionMapper).insert(captor.capture());
        assertThat(captor.getValue()).extracting(EduRegionPo::getParentId, EduRegionPo::getAncestors, EduRegionPo::getNodeLevel)
                .containsExactly(10L, "0,10", 1);
    }

    @Test
    void rejectsMoveUnderDescendant() {
        when(regionMapper.selectById(1L)).thenReturn(region(1L, null, "重庆市"));
        when(regionMapper.selectById(2L)).thenReturn(region(2L, 1L, "两江新区"));

        assertThatThrownBy(() -> service.save(form(1L, 2L, "重庆市")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("自身或其下级");
    }

    @Test
    void loadsOnlyRequestedParentOptions() {
        when(regionMapper.selectList(any())).thenReturn(List.of(region(3101L, 31L, "上海市市辖区")));

        assertThat(service.options(null, 31L))
                .extracting(EduRegionPo::getId, EduRegionPo::getRegionName)
                .containsExactly(tuple(3101L, "上海市市辖区"));
    }

    @Test
    void returnsSelectedRegionPathFromRoot() {
        when(regionMapper.selectById(310101L)).thenReturn(region(310101L, 3101L, "黄浦区"));
        when(regionMapper.selectById(3101L)).thenReturn(region(3101L, 31L, "上海市市辖区"));
        when(regionMapper.selectById(31L)).thenReturn(region(31L, null, "上海市"));

        assertThat(service.path(310101L)).extracting(EduRegionPo::getId)
                .containsExactly(31L, 3101L, 310101L);
    }

    @Test
    void returnsFullPathForRegionSearch() {
        EduRegionPo county = region(500230L, 5001L, "丰都县");
        county.setAncestors("0,50,5001");
        when(regionMapper.selectList(any()))
                .thenReturn(List.of(county))
                .thenReturn(List.of(region(50L, null, "重庆市"), region(5001L, 50L, "重庆市市辖区"), county));

        assertThat(service.searchOptions("丰都"))
                .extracting(EducationRegionSearchOption::pathLabel)
                .containsExactly("重庆市 > 重庆市市辖区 > 丰都县");
    }

    private static EducationRegionForms.Region form(Long id, Long parentId, String name) {
        return new EducationRegionForms.Region(id, parentId, name, "DISTRICT", 0, 0, null);
    }

    private static EduRegionPo region(Long id, Long parentId, String name) {
        EduRegionPo item = new EduRegionPo();
        item.setId(id);
        item.setParentId(parentId);
        item.setRegionName(name);
        item.setRegionCode("REGION_" + id);
        return item;
    }
}
