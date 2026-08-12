package com.han.system.sdfz.education;

import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.ConflictException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EducationForms;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduPersonSubjectMapper;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduSubjectMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationMasterDataServiceTest {

    @Mock
    private EduSchoolMapper schoolMapper;
    @Mock
    private EduClassMapper classMapper;
    @Mock
    private EduPersonMapper personMapper;
    @Mock
    private EduSubjectMapper subjectMapper;
    @Mock
    private EduDeviceMapper deviceMapper;
    @Mock
    private EduRoomMapper roomMapper;
    @Mock
    private EduPersonClassMapper personClassMapper;
    @Mock
    private EduPersonSubjectMapper personSubjectMapper;

    private EducationMasterDataService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(2L).tenantId(1L).build());
        service = new EducationMasterDataService(schoolMapper, classMapper, personMapper, subjectMapper,
                deviceMapper, roomMapper, personClassMapper, personSubjectMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void createsLocalSchoolInsideCurrentTenant() {
        when(schoolMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            EduSchoolPo value = invocation.getArgument(0);
            value.setId(11L);
            return 1;
        }).when(schoolMapper).insert(any(EduSchoolPo.class));

        Long id = service.saveSchool(new EducationForms.School(
                null, null, " S001 ", " School One ", "MAIN", null, 0, null));

        ArgumentCaptor<EduSchoolPo> captor = ArgumentCaptor.forClass(EduSchoolPo.class);
        verify(schoolMapper).insert(captor.capture());
        assertThat(id).isEqualTo(11L);
        assertThat(captor.getValue())
                .extracting(EduSchoolPo::getTenantId, EduSchoolPo::getSourceSystem,
                        EduSchoolPo::getSchoolCode, EduSchoolPo::getSchoolName)
                .containsExactly(1L, "HAN", "S001", "School One");
    }

    @Test
    void rejectsDuplicateSchoolCodeBeforeHittingDatabase() {
        when(schoolMapper.selectCount(any())).thenReturn(1L);

        EducationForms.School form = new EducationForms.School(
                null, null, "S001", "School One", "MAIN", null, 0, null);

        assertThatThrownBy(() -> service.saveSchool(form))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("学校编码");
        verify(schoolMapper, never()).insert(any(EduSchoolPo.class));
    }

    @Test
    void rejectsEditingDigitalCampusOwnedSchool() {
        EduSchoolPo existing = new EduSchoolPo();
        existing.setId(11L);
        existing.setSourceSystem("DIGITAL_CAMPUS");
        when(schoolMapper.selectById(11L)).thenReturn(existing);

        EducationForms.School form = new EducationForms.School(
                11L, null, "S001", "School One", "MAIN", null, 0, null);

        assertThatThrownBy(() -> service.saveSchool(form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数字校园");
        verify(schoolMapper, never()).updateById(any(EduSchoolPo.class));
    }

    @Test
    void requiresAuthenticatedTenantContext() {
        SecurityContextHolder.clear();

        assertThatThrownBy(() -> service.listSchools(null, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户");
    }

    @Test
    void rejectsDeletingSchoolThatStillHasClasses() {
        when(schoolMapper.selectById(11L)).thenReturn(localSchool(11L));
        when(schoolMapper.selectCount(any())).thenReturn(0L);
        when(classMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteSchools(List.of(11L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("班级");
        verify(schoolMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void keepsBusinessCodeUnchangedOnDelete() {
        when(schoolMapper.selectById(11L)).thenReturn(localSchool(11L));
        when(schoolMapper.selectCount(any())).thenReturn(0L);
        when(classMapper.selectCount(any())).thenReturn(0L);
        when(personMapper.selectCount(any())).thenReturn(0L);
        when(roomMapper.selectCount(any())).thenReturn(0L);
        when(deviceMapper.selectCount(any())).thenReturn(0L);
        when(schoolMapper.deleteById(11L)).thenReturn(1);

        int removed = service.deleteSchools(List.of(11L));

        assertThat(removed).isEqualTo(1);
        verify(schoolMapper, never()).updateById(any(EduSchoolPo.class));
    }

    @Test
    void skipsMissingIdsSoDeleteStaysIdempotent() {
        when(schoolMapper.selectById(99L)).thenReturn(null);

        int removed = service.deleteSchools(List.of(99L));

        assertThat(removed).isZero();
        verify(schoolMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void rejectsDeletingDigitalCampusOwnedClass() {
        EduClassPo item = new EduClassPo();
        item.setId(21L);
        item.setSourceSystem("DIGITAL_CAMPUS");
        when(classMapper.selectById(21L)).thenReturn(item);

        assertThatThrownBy(() -> service.deleteClasses(List.of(21L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数字校园");
        verify(classMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void rejectsDeletingClassStillReferencedByTeachingAssignment() {
        EduClassPo item = new EduClassPo();
        item.setId(21L);
        item.setSourceSystem("HAN");
        when(classMapper.selectById(21L)).thenReturn(item);
        when(personClassMapper.selectCount(any())).thenReturn(0L);
        when(personSubjectMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteClasses(List.of(21L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任教关系");
        verify(classMapper, never()).deleteById(any(Long.class));
    }

    // 学期用例随 saveSemester 一并移交 EducationCalendarServiceTest：
    // 学期写入已由 EducationCalendarService 独占，本类不再有该路径。

    private static EduSchoolPo localSchool(Long id) {
        EduSchoolPo school = new EduSchoolPo();
        school.setId(id);
        school.setSourceSystem("HAN");
        school.setSchoolCode("S001");
        return school;
    }
}
