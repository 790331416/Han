package com.han.system.sdfz.education;

import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.ConflictException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.sdfz.education.domain.EduAcademicYearPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduSemesterPo;
import com.han.system.sdfz.education.domain.EducationAcademicYearForms;
import com.han.system.sdfz.education.mapper.EduAcademicYearMapper;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduGradePromotionBatchMapper;
import com.han.system.sdfz.education.mapper.EduSemesterMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("租户统一学年管理")
class EducationAcademicYearServiceTest {

    @Mock
    private EduAcademicYearMapper academicYearMapper;
    @Mock
    private EduSemesterMapper semesterMapper;
    @Mock
    private EduClassMapper classMapper;
    @Mock
    private EduGradePromotionBatchMapper promotionBatchMapper;
    @Mock
    private EduSchoolMapper schoolMapper;
    @Mock
    private EducationDataScopeService dataScopeService;

    private EducationAcademicYearService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(2L).tenantId(1L).build());
        service = new EducationAcademicYearService(academicYearMapper, semesterMapper, classMapper, promotionBatchMapper, schoolMapper, dataScopeService);
        EduSchoolPo school = new EduSchoolPo();
        school.setId(7L);
        school.setOrgType("SCHOOL");
        school.setSchoolManageType("INDEPENDENT");
        when(schoolMapper.selectById(7L)).thenReturn(school);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    @DisplayName("新建草稿学年写入当前租户且保留日期区间")
    void createsDraftForCurrentTenant() {
        when(academicYearMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            EduAcademicYearPo item = invocation.getArgument(0);
            item.setId(100L);
            return 1;
        }).when(academicYearMapper).insert(any(EduAcademicYearPo.class));

        Long id = service.save(form("DRAFT"));

        assertThat(id).isEqualTo(100L);
        ArgumentCaptor<EduAcademicYearPo> captured = ArgumentCaptor.forClass(EduAcademicYearPo.class);
        verify(academicYearMapper).insert(captured.capture());
        assertThat(captured.getValue())
                .extracting(EduAcademicYearPo::getTenantId, EduAcademicYearPo::getYearCode,
                        EduAcademicYearPo::getStatus)
                .containsExactly(1L, "2026-2027", "DRAFT");
    }

    @Test
    @DisplayName("同一租户不能同时启用两个学年")
    void rejectsSecondActiveYear() {
        when(academicYearMapper.selectCount(any())).thenReturn(0L, 1L);

        assertThatThrownBy(() -> service.save(form("ACTIVE")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("启用中的学年");
        verify(academicYearMapper, never()).insert(any(EduAcademicYearPo.class));
    }

    @Test
    @DisplayName("中心校不能承载学年业务数据")
    void rejectsCenterSchool() {
        EduSchoolPo center = new EduSchoolPo();
        center.setId(7L);
        center.setOrgType("SCHOOL");
        center.setSchoolManageType("CENTER");
        when(schoolMapper.selectById(7L)).thenReturn(center);

        assertThatThrownBy(() -> service.save(form("DRAFT")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学校不存在或不在当前数据范围");
        verify(academicYearMapper, never()).insert(any(EduAcademicYearPo.class));
    }

    @Test
    @DisplayName("学年已被学期引用时拒绝删除")
    void rejectsDeletionWhenSemesterReferencesYear() {
        EduAcademicYearPo year = new EduAcademicYearPo();
        year.setId(100L); year.setSchoolId(7L);
        when(academicYearMapper.selectById(100L)).thenReturn(year);
        when(semesterMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(List.of(100L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学期");
        verify(academicYearMapper, never()).deleteById(100L);
    }

    private static EducationAcademicYearForms.AcademicYear form(String status) {
        return new EducationAcademicYearForms.AcademicYear(
                null, 7L, "2026-2027", "2026-2027 学年",
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31), status, null);
    }
}
