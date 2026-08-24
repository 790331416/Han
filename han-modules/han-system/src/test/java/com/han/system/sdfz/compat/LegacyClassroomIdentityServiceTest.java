package com.han.system.sdfz.compat;

import com.han.api.system.domain.ClassroomIdentityVO;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyClassroomIdentityServiceTest {

    @Mock
    private LegacyDirectoryService directoryService;

    @Test
    void listsAndResolvesAllActiveTeacherAndStudentIdentities() {
        LegacyCompatProperties properties = new LegacyCompatProperties();
        EduPersonPo teacher = person(11L, "TEACHER", 0, 0);
        EduPersonPo student = person(21L, "STUDENT", 0, 0);
        EduPersonPo leftStudent = person(22L, "STUDENT", 0, 1);
        EduPersonPo missingStatus = person(23L, "STUDENT", null, 0);
        when(directoryService.personsByUserId(100L)).thenReturn(List.of(teacher, student, leftStudent, missingStatus));
        when(directoryService.schoolById(7L)).thenReturn(school());
        when(directoryService.externalUserId(teacher)).thenReturn("100");
        when(directoryService.externalUserId(student)).thenReturn("100");

        LegacyClassroomIdentityService service = new LegacyClassroomIdentityService(properties, directoryService);

        List<ClassroomIdentityVO> identities = service.list(100L);

        assertThat(identities).extracting(ClassroomIdentityVO::getIdentityId).containsExactly("11", "21");
        assertThat(identities).extracting(ClassroomIdentityVO::isLoginAllowed).containsExactly(true, true);
        assertThat(service.resolve(100L).getIdentityId()).isEqualTo("11");
        assertThat(service.resolve(100L, "21").getIdentityId()).isEqualTo("21");
    }

    @Test
    void resolveByExternalMatchesStableExternalIdentityIdOnly() {
        LegacyCompatProperties properties = new LegacyCompatProperties();
        EduPersonPo digitalCampus = person(11L, "TEACHER", 0, 0);
        digitalCampus.setSourceSystem("DIGITAL_CAMPUS");
        digitalCampus.setExternalIdentityId("ext-identity-1");
        EduPersonPo localWithoutExternal = person(12L, "TEACHER", 0, 0);
        EduPersonPo otherExternal = person(13L, "TEACHER", 0, 0);
        otherExternal.setSourceSystem("DIGITAL_CAMPUS");
        otherExternal.setExternalIdentityId("ext-identity-2");
        when(directoryService.personsByUserId(100L))
                .thenReturn(List.of(digitalCampus, localWithoutExternal, otherExternal));
        when(directoryService.schoolById(7L)).thenReturn(school());
        when(directoryService.externalUserId(digitalCampus)).thenReturn("100");
        when(directoryService.externalUserId(otherExternal)).thenReturn("100");

        LegacyClassroomIdentityService service = new LegacyClassroomIdentityService(properties, directoryService);

        ClassroomIdentityVO resolved = service.resolveByExternal(100L, "ext-identity-1");

        assertThat(resolved).isNotNull();
        assertThat(resolved.getIdentityId()).isEqualTo("11");
        assertThat(resolved.getExternalIdentityId()).isEqualTo("ext-identity-1");
        assertThat(service.resolveByExternal(100L, "ext-identity-2").getIdentityId()).isEqualTo("13");
        // 非数字校园来源 / 未知外部身份 ID 不参与匹配。
        assertThat(service.resolveByExternal(100L, "unknown")).isNull();
        assertThat(service.resolveByExternal(100L, null)).isNull();
    }

    private static EduPersonPo person(Long id, String type, Integer status, Integer leaveFlag) {
        EduPersonPo person = new EduPersonPo();
        person.setId(id);
        person.setUserId(100L);
        person.setPersonName(type + " 用户");
        person.setPersonType(type);
        person.setSchoolId(7L);
        person.setStatus(status);
        person.setLeaveFlag(leaveFlag);
        return person;
    }

    private static EduSchoolPo school() {
        EduSchoolPo school = new EduSchoolPo();
        school.setId(7L);
        school.setSchoolName("巴蜀云校");
        return school;
    }
}
