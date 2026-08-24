package com.han.system.sdfz.education;

import com.han.api.system.AuthServiceClient;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduRegionPo;
import com.han.system.sdfz.education.domain.EducationOrganizationForms;
import com.han.system.sdfz.education.domain.EducationOrganizationNode;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduRegionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("教育组织树管理")
class EducationOrganizationTreeServiceTest {

    @Mock
    private EduSchoolMapper schoolMapper;
    @Mock
    private EduRegionMapper regionMapper;
    @Mock
    private EduPersonMapper personMapper;

    @Mock
    private EducationDataScopeService dataScopeService;
    @Mock
    private AuthServiceClient authServiceClient;

    private EducationOrganizationTreeService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(2L).tenantId(1L).build());
        service = new EducationOrganizationTreeService(schoolMapper, regionMapper, personMapper,
                dataScopeService, authServiceClient);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    @DisplayName("学校挂到教育局时按父链生成祖先路径")
    void createsSchoolBelowBureau() {
        when(regionMapper.selectById(1L)).thenReturn(region(1L, "500000"));
        EduSchoolPo bureau = school(10L, null, "EDU_BUREAU", "教育局");
        bureau.setRegionId(1L);
        when(schoolMapper.selectById(10L)).thenReturn(bureau);
        when(schoolMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            EduSchoolPo value = invocation.getArgument(0);
            value.setId(20L);
            return 1;
        }).when(schoolMapper).insert(any(EduSchoolPo.class));

        Long id = service.save(form(null, 10L, "两江中学", "SCHOOL"));

        assertThat(id).isEqualTo(20L);
        ArgumentCaptor<EduSchoolPo> captured = ArgumentCaptor.forClass(EduSchoolPo.class);
        verify(schoolMapper).insert(captured.capture());
        assertThat(captured.getValue())
                .extracting(EduSchoolPo::getParentId, EduSchoolPo::getAncestors,
                        EduSchoolPo::getNodeLevel, EduSchoolPo::getOrgType)
                .containsExactly(10L, "0,10", 1, "SCHOOL");
    }

    @Test
    @DisplayName("不能把学校移动到自己的下级校区下")
    void rejectsMoveUnderDescendant() {
        when(regionMapper.selectById(1L)).thenReturn(region(1L, "500000"));
        EduSchoolPo school = school(1L, null, "SCHOOL", "两江大学");
        EduSchoolPo campus = school(2L, 1L, "SCHOOL", "两江大学北校区");
        school.setRegionId(1L);
        campus.setRegionId(1L);
        when(schoolMapper.selectById(1L)).thenReturn(school);
        when(schoolMapper.selectById(2L)).thenReturn(campus);

        assertThatThrownBy(() -> service.save(form(1L, 2L, "两江大学", "SCHOOL")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("自身或其下级");
    }

    @Test
    @DisplayName("下级组织不能绑定到上级区域之外")
    void rejectsRegionOutsideParentRegion() {
        EduRegionPo parentRegion = region(1L, "50");
        EduRegionPo outsideRegion = region(2L, "62");
        when(regionMapper.selectById(1L)).thenReturn(parentRegion);
        when(regionMapper.selectById(2L)).thenReturn(outsideRegion);
        EduSchoolPo bureau = school(10L, null, "EDU_BUREAU", "重庆市教育局");
        bureau.setRegionId(1L);
        when(schoolMapper.selectById(10L)).thenReturn(bureau);

        EducationOrganizationForms.Organization form = new EducationOrganizationForms.Organization(
                null, 10L, "甘肃学校", "SCHOOL", "INDEPENDENT", "3", 2L, 1, 0, null);

        assertThatThrownBy(() -> service.save(form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("属于上级组织区域");
    }

    @Test
    @DisplayName("修改教育局区域时不能甩开现有下级学校")
    void rejectsRegionThatExcludesExistingChildren() {
        EduSchoolPo bureau = school(10L, null, "EDU_BUREAU", "两江新区教育局");
        bureau.setRegionId(1L);
        EduSchoolPo child = school(20L, 10L, "SCHOOL", "两江中学");
        child.setRegionId(1L);
        child.setAncestors("0,10");
        when(schoolMapper.selectById(10L)).thenReturn(bureau);
        when(regionMapper.selectById(2L)).thenReturn(region(2L, "62"));
        when(regionMapper.selectById(1L)).thenReturn(region(1L, "50"));
        when(schoolMapper.selectList(any())).thenReturn(List.of(child));

        EducationOrganizationForms.Organization changed = new EducationOrganizationForms.Organization(
                10L, null, "两江新区教育局", "EDU_BUREAU", null, null, 2L, 1, 0, null);

        assertThatThrownBy(() -> service.save(changed))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能覆盖现有下级组织");
    }

    @Test
    @DisplayName("组织树按父子关系返回稳定层级")
    void buildsTree() {
        EduSchoolPo bureau = school(1L, null, "EDU_BUREAU", "教育局");
        EduSchoolPo school = school(2L, 1L, "SCHOOL", "两江中学");
        when(dataScopeService.current()).thenReturn(new EducationDataScopeService.Scope(true, java.util.Set.of(), java.util.Set.of()));
        when(schoolMapper.selectList(any())).thenReturn(List.of(bureau, school));

        List<EducationOrganizationNode> nodes = service.tree(0);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.getFirst().children()).extracting(EducationOrganizationNode::id).containsExactly(2L);
    }

    @Test
    @DisplayName("直属学校授权补齐教育局祖先但不扩大业务学校")
    void addsBureauAncestorsForDisplay() {
        EduSchoolPo bureau = school(1L, null, "EDU_BUREAU", "重庆市教育局");
        EduSchoolPo school = school(2L, 1L, "SCHOOL", "两江中学");
        school.setAncestors("0,1");
        when(dataScopeService.current()).thenReturn(new EducationDataScopeService.Scope(false, java.util.Set.of(2L), java.util.Set.of(2L)));
        when(schoolMapper.selectList(any())).thenReturn(List.of(school));
        when(schoolMapper.selectBatchIds(any())).thenReturn(List.of(bureau));

        List<EducationOrganizationNode> nodes = service.tree(0);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.getFirst().children()).extracting(EducationOrganizationNode::id).containsExactly(2L);
    }

    private static EducationOrganizationForms.Organization form(Long id, Long parentId, String name, String orgType) {
        return new EducationOrganizationForms.Organization(
                id, parentId, name, orgType, "INDEPENDENT", "3", 1L, 1, 0, null);
    }

    private static EduSchoolPo school(Long id, Long parentId, String orgType, String name) {
        EduSchoolPo item = new EduSchoolPo();
        item.setId(id);
        item.setParentId(parentId);
        item.setOrgType(orgType);
        item.setSchoolName(name);
        item.setSchoolCode("ORG_" + id);
        item.setSourceSystem("HAN");
        item.setNodeLevel(parentId == null ? 0 : 1);
        return item;
    }

    private static EduRegionPo region(Long id, String code) {
        EduRegionPo item = new EduRegionPo();
        item.setId(id);
        item.setRegionCode(code);
        item.setStatus(0);
        return item;
    }
}
