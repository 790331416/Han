package com.han.system.sdfz.digitalcampus;

import com.han.api.system.domain.DigitalCampusUserSyncDTO;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduPersonClassPo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DigitalCampusEducationSyncServiceTest {

    @Mock
    private EduSchoolMapper schoolMapper;
    @Mock
    private EduClassMapper classMapper;
    @Mock
    private EduPersonMapper personMapper;
    @Mock
    private EduPersonClassMapper personClassMapper;

    private DigitalCampusEducationSyncService service;

    @BeforeEach
    void setUp() {
        service = new DigitalCampusEducationSyncService(
                schoolMapper, classMapper, personMapper, personClassMapper);
    }

    @Test
    void firstLoginCreatesSchoolClassPersonAndMembership() {
        doAnswer(invocation -> assignId(invocation.getArgument(0), 11L))
                .when(schoolMapper).insert(any(EduSchoolPo.class));
        doAnswer(invocation -> assignId(invocation.getArgument(0), 21L))
                .when(classMapper).insert(any(EduClassPo.class));
        doAnswer(invocation -> assignId(invocation.getArgument(0), 31L))
                .when(personMapper).insert(any(EduPersonPo.class));
        doAnswer(invocation -> assignId(invocation.getArgument(0), 41L))
                .when(personClassMapper).insert(any(EduPersonClassPo.class));

        service.sync(syncDto(), 100L);

        ArgumentCaptor<EduSchoolPo> school = ArgumentCaptor.forClass(EduSchoolPo.class);
        ArgumentCaptor<EduClassPo> classInfo = ArgumentCaptor.forClass(EduClassPo.class);
        ArgumentCaptor<EduPersonPo> person = ArgumentCaptor.forClass(EduPersonPo.class);
        ArgumentCaptor<EduPersonClassPo> membership = ArgumentCaptor.forClass(EduPersonClassPo.class);
        verify(schoolMapper).insert(school.capture());
        verify(classMapper).insert(classInfo.capture());
        verify(personMapper).insert(person.capture());
        verify(personClassMapper).insert(membership.capture());

        assertThat(school.getValue().getSourceSystem()).isEqualTo("DIGITAL_CAMPUS");
        assertThat(school.getValue().getExternalId()).isEqualTo("school-1");
        assertThat(classInfo.getValue().getSchoolId()).isEqualTo(11L);
        assertThat(classInfo.getValue().getExternalId()).isEqualTo("class-1");
        assertThat(person.getValue().getUserId()).isEqualTo(100L);
        assertThat(person.getValue().getExternalIdentityId()).isEqualTo("identity-1");
        assertThat(membership.getValue())
                .extracting(EduPersonClassPo::getPersonId, EduPersonClassPo::getClassId,
                        EduPersonClassPo::getMembershipRole)
                .containsExactly(31L, 21L, "TEACHER");
    }

    @Test
    void repeatedLoginUpdatesExistingRowsWithoutDuplicatingMembership() {
        EduSchoolPo school = new EduSchoolPo();
        school.setId(11L);
        school.setStatus(1);
        EduClassPo classInfo = new EduClassPo();
        classInfo.setId(21L);
        classInfo.setStatus(1);
        EduPersonPo person = new EduPersonPo();
        person.setId(31L);
        person.setStatus(1);
        EduPersonClassPo membership = new EduPersonClassPo();
        membership.setId(41L);
        when(schoolMapper.selectOne(any())).thenReturn(school);
        when(classMapper.selectOne(any())).thenReturn(classInfo);
        when(personMapper.selectOne(any())).thenReturn(person);
        when(personClassMapper.selectOne(any())).thenReturn(membership);

        service.sync(syncDto(), 100L);

        verify(schoolMapper).updateById(school);
        verify(classMapper).updateById(classInfo);
        verify(personMapper).updateById(person);
        verify(personClassMapper, never()).insert(any(EduPersonClassPo.class));
        assertThat(school.getStatus()).isEqualTo(1);
        assertThat(classInfo.getStatus()).isEqualTo(1);
        assertThat(person.getStatus()).isEqualTo(1);
    }

    private static int assignId(Object value, Long id) {
        if (value instanceof EduSchoolPo item) item.setId(id);
        else if (value instanceof EduClassPo item) item.setId(id);
        else if (value instanceof EduPersonPo item) item.setId(id);
        else if (value instanceof EduPersonClassPo item) item.setId(id);
        return 1;
    }

    private static DigitalCampusUserSyncDTO syncDto() {
        DigitalCampusUserSyncDTO.ClassMembership membership = DigitalCampusUserSyncDTO.ClassMembership.builder()
                .branchId("class-1")
                .branchName("Class One")
                .classRoleId("TEACHER")
                .schoolId("school-1")
                .schoolName("School One")
                .areaCode("500100")
                .build();
        return DigitalCampusUserSyncDTO.builder()
                .tenantId(1L)
                .externalUserId("external-user-1")
                .externalIdentityId("identity-1")
                .userName("Teacher One")
                .phone("13800000000")
                .identityName("Teacher")
                .roleType("TEACHER")
                .schoolId("school-1")
                .schoolName("School One")
                .branchId("class-1")
                .branchName("Class One")
                .areaCode("500100")
                .classes(List.of(membership))
                .duties(List.of())
                .build();
    }
}
