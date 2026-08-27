package com.han.system.sdfz.education;

import com.han.common.core.exception.BusinessException;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EducationOpenDirectoryServiceTest {

    @Test
    void resolvesOnlyAuthorizedSchoolNames() {
        EduSchoolMapper schoolMapper = mock(EduSchoolMapper.class);
        EducationOpenDirectoryService service = service(schoolMapper);
        EduSchoolPo first = new EduSchoolPo();
        first.setId(1001L);
        first.setSchoolName("鲁巴数智教育中心");
        EduSchoolPo second = new EduSchoolPo();
        second.setId(1002L);
        second.setSchoolName("两江中学");
        when(schoolMapper.selectList(any())).thenReturn(List.of(first, second));

        assertThat(service.schoolNames(99L, List.of(1002L, 1001L, 9999L)))
                .containsExactly(Map.entry(1002L, "两江中学"), Map.entry(1001L, "鲁巴数智教育中心"));
    }

    @Test
    void failsClosedWhenAnOpenApplicationHasNoSchoolScope() {
        EduPersonMapper personMapper = mock(EduPersonMapper.class);
        EducationOpenDirectoryService service = service(mock(EduSchoolMapper.class), personMapper);

        assertThatThrownBy(() -> service.people(1L, List.of(), "TEACHER", 0, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("开放目录未授权任何学校");
        verifyNoInteractions(personMapper);
    }

    @Test
    void exactDeviceLookupReturnsNullWhenNoAuthorizedDeviceMatches() {
        EduDeviceMapper deviceMapper = mock(EduDeviceMapper.class);
        EducationOpenDirectoryService service = new EducationOpenDirectoryService(
                mock(EduPersonMapper.class), mock(EduPersonClassMapper.class), mock(EduClassMapper.class),
                deviceMapper, mock(EduSchoolMapper.class), mock(EduRoomMapper.class));
        when(deviceMapper.selectOne(any())).thenReturn(null);

        assertThat(service.device(1L, List.of(1001L), "MISSING")).isNull();
        verify(deviceMapper).selectOne(any());
    }

    private static EducationOpenDirectoryService service(EduSchoolMapper schoolMapper) {
        return service(schoolMapper, mock(EduPersonMapper.class));
    }

    private static EducationOpenDirectoryService service(EduSchoolMapper schoolMapper, EduPersonMapper personMapper) {
        return new EducationOpenDirectoryService(personMapper, mock(EduPersonClassMapper.class),
                mock(EduClassMapper.class), mock(EduDeviceMapper.class), schoolMapper, mock(EduRoomMapper.class));
    }
}
