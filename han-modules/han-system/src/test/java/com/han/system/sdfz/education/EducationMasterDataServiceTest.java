package com.han.system.sdfz.education;

import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EducationForms;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduSubjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private EducationMasterDataService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(2L).tenantId(1L).build());
        service = new EducationMasterDataService(
                schoolMapper, classMapper, personMapper, subjectMapper, deviceMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void createsLocalSchoolInsideCurrentTenant() {
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
    void rejectsEditingDigitalCampusOwnedSchool() {
        EduSchoolPo existing = new EduSchoolPo();
        existing.setId(11L);
        existing.setSourceSystem("DIGITAL_CAMPUS");
        when(schoolMapper.selectById(11L)).thenReturn(existing);

        EducationForms.School form = new EducationForms.School(
                11L, null, "S001", "School One", "MAIN", null, 0, null);

        assertThatThrownBy(() -> service.saveSchool(form))
                .isInstanceOf(BusinessException.class);
        verify(schoolMapper, never()).updateById(any(EduSchoolPo.class));
    }

    @Test
    void requiresAuthenticatedTenantContext() {
        SecurityContextHolder.clear();

        assertThatThrownBy(() -> service.listSchools(null, null, 1, 20))
                .isInstanceOf(BusinessException.class);
    }
}
