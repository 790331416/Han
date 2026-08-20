package com.han.system.sdfz.education;

import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduAcademicYearPo;
import com.han.system.sdfz.education.domain.EducationClassTreeForms;
import com.han.system.sdfz.education.domain.EducationClassTreeNode;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduAcademicYearMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationClassTreeServiceTest {
    @Mock private EduClassMapper classMapper;
    @Mock private EduSchoolMapper schoolMapper;
    @Mock private EduAcademicYearMapper academicYearMapper;
    @Mock private EducationDataScopeService dataScopeService;
    private EducationClassTreeService service;

    @BeforeEach void setUp() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(2L).tenantId(1L).build());
        service = new EducationClassTreeService(classMapper, schoolMapper, academicYearMapper, dataScopeService);
        EduSchoolPo school = new EduSchoolPo(); school.setId(7L); school.setOrgType("SCHOOL"); school.setSchoolManageType("INDEPENDENT");
        when(schoolMapper.selectById(7L)).thenReturn(school);
    }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test
    void createsClassBelowGradeAndInheritsGradeCode() {
        stubAcademicYear();
        EduClassPo grade = node(10L, null, "GRADE", "G010", "G010");
        when(classMapper.selectById(10L)).thenReturn(grade);
        when(classMapper.selectCount(any())).thenReturn(0L);
        doAnswer(call -> { ((EduClassPo) call.getArgument(0)).setId(20L); return 1; }).when(classMapper).insert(any(EduClassPo.class));

        Long id = service.save(form(null, 10L, "七年级一班", "CLASS", null));

        ArgumentCaptor<EduClassPo> captor = ArgumentCaptor.forClass(EduClassPo.class);
        org.mockito.Mockito.verify(classMapper).insert(captor.capture());
        assertThat(id).isEqualTo(20L);
        assertThat(captor.getValue()).extracting(EduClassPo::getParentId, EduClassPo::getGradeCode, EduClassPo::getNodeType)
                .containsExactly(10L, "G010", "CLASS");
    }

    @Test
    void rejectsChildBelowClassLeaf() {
        stubAcademicYear();
        when(classMapper.selectById(10L)).thenReturn(node(10L, null, "CLASS", null, null));

        assertThatThrownBy(() -> service.save(form(null, 10L, "不应存在的下级", "CLASS", null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("叶子节点");
    }

    @Test
    void rejectsMissingAcademicYear() {
        assertThatThrownBy(() -> service.save(new EducationClassTreeForms.Node(
                null, 7L, null, null, "七年级", "GRADE", "G010", 2026, "NORMAL", 0, 0, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("学年");
    }

    @Test
    void buildsTreeFromParentRelation() {
        EduClassPo grade = node(10L, null, "GRADE", "G010", "G010");
        EduClassPo clazz = node(20L, 10L, "CLASS", null, "G010");
        when(classMapper.selectList(any())).thenReturn(List.of(grade, clazz));

        List<EducationClassTreeNode> nodes = service.tree(7L, 1L, 0);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.getFirst().children()).extracting(EducationClassTreeNode::id).containsExactly(20L);
    }

    @Test
    void batchCreatesStandardGradesWithNumberAsSort() {
        stubAcademicYear();
        when(classMapper.selectCount(any())).thenReturn(0L);
        doAnswer(call -> { ((EduClassPo) call.getArgument(0)).setId(20L); return 1; }).when(classMapper).insert(any(EduClassPo.class));

        int created = service.createRange(new EducationClassTreeForms.Range(7L, 1L, null, "GRADE", 2026, 1, 2, 0));

        ArgumentCaptor<EduClassPo> captor = ArgumentCaptor.forClass(EduClassPo.class);
        org.mockito.Mockito.verify(classMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(created).isEqualTo(2);
        assertThat(captor.getAllValues()).extracting(EduClassPo::getClassName, EduClassPo::getSort)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("一年级", 1), org.assertj.core.groups.Tuple.tuple("二年级", 2));
    }

    private static EducationClassTreeForms.Node form(Long id, Long parentId, String name, String type, String branchCode) {
        return new EducationClassTreeForms.Node(id, 7L, parentId, 1L, name, type, branchCode, 2026, "NORMAL", 0, 0, null);
    }
    private static EduClassPo node(Long id, Long parentId, String type, String branchCode, String gradeCode) {
        EduClassPo value = new EduClassPo();
        value.setId(id); value.setSchoolId(7L); value.setParentId(parentId); value.setAcademicYearId(1L);
        value.setNodeType(type); value.setBranchCode(branchCode); value.setGradeCode(gradeCode); value.setCohortYear(2026); value.setClassName("节点" + id);
        value.setClassCode("C" + id); value.setSourceSystem("HAN"); value.setStatus(0); return value;
    }
    private void stubAcademicYear() {
        EduAcademicYearPo year = new EduAcademicYearPo(); year.setId(1L); year.setSchoolId(7L);
        when(academicYearMapper.selectById(1L)).thenReturn(year);
    }
}
