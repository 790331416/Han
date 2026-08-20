package com.han.system.sdfz.education;

import com.han.common.core.exception.BusinessException;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class EducationOpenDirectoryServiceTest {

    @Test
    void failsClosedWhenAnOpenApplicationHasNoSchoolScope() {
        EduPersonMapper personMapper = mock(EduPersonMapper.class);
        EducationOpenDirectoryService service = new EducationOpenDirectoryService(
                personMapper,
                mock(EduPersonClassMapper.class),
                mock(EduClassMapper.class),
                mock(EduDeviceMapper.class),
                mock(EduSchoolMapper.class),
                mock(EduRoomMapper.class));

        assertThatThrownBy(() -> service.people(1L, List.of(), "TEACHER", 0, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("开放目录未授权任何学校");
        verifyNoInteractions(personMapper);
    }
}
