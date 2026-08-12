package com.han.system.sdfz.education;

import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.sdfz.education.domain.EduRoomPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduSemesterPo;
import com.han.system.sdfz.education.domain.EducationCalendarForms;
import com.han.system.sdfz.education.domain.SemesterLifecycle;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduSemesterMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("学期与教室管理的校验")
class EducationCalendarServiceTest {

    @Mock
    private EduSemesterMapper semesterMapper;
    @Mock
    private EduRoomMapper roomMapper;
    @Mock
    private EduSchoolMapper schoolMapper;
    @Mock
    private EduDeviceMapper deviceMapper;

    private EducationCalendarService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(2L).tenantId(1L).build());
        service = new EducationCalendarService(semesterMapper, roomMapper, schoolMapper, deviceMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    @DisplayName("新建学期时按当天推出阶段，写的是 lifecycleStatus 而不是 status")
    void derivesLifecycleOnCreate() {
        doAnswer(invocation -> {
            EduSemesterPo value = invocation.getArgument(0);
            value.setId(31L);
            return 1;
        }).when(semesterMapper).insert(any(EduSemesterPo.class));

        LocalDate today = LocalDate.now();
        service.saveSemester(new EducationCalendarForms.Semester(
                null, " 2026-1 ", " 上学期 ", today.minusDays(1), today.plusDays(30), 0, 0, null));

        ArgumentCaptor<EduSemesterPo> captor = ArgumentCaptor.forClass(EduSemesterPo.class);
        verify(semesterMapper).insert(captor.capture());
        EduSemesterPo saved = captor.getValue();
        assertThat(saved.getLifecycleStatus()).isEqualTo(SemesterLifecycle.IN_PROGRESS.name());
        assertThat(saved.getStatus()).as("status 表示记录是否启用，与阶段无关").isZero();
        assertThat(saved.getSemesterCode()).isEqualTo("2026-1");
        assertThat(saved.getTenantId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("结束日期早于开始日期直接拒绝")
    void rejectsInvertedDateRange() {
        LocalDate today = LocalDate.now();
        EducationCalendarForms.Semester form = new EducationCalendarForms.Semester(
                null, "2026-1", "上学期", today, today.minusDays(1), 0, 0, null);

        assertThatThrownBy(() -> service.saveSemester(form)).isInstanceOf(BusinessException.class);
        verify(semesterMapper, never()).insert(any(EduSemesterPo.class));
    }

    @Test
    @DisplayName("阶段已经正确的学期不会被再更新一遍")
    void lifecycleAdvanceIsIdempotent() {
        LocalDate today = LocalDate.of(2026, 10, 1);
        EduSemesterPo current = new EduSemesterPo();
        current.setId(1L);
        current.setBeginDate(today.minusDays(10));
        current.setEndDate(today.plusDays(10));
        current.setLifecycleStatus(SemesterLifecycle.IN_PROGRESS.name());
        when(semesterMapper.selectList(any())).thenReturn(java.util.List.of(current));

        assertThat(service.advanceSemesterLifecycle(today)).isZero();
        verify(semesterMapper, never()).updateById(any(EduSemesterPo.class));
    }

    @Test
    @DisplayName("教室必须挂在存在的学校下")
    void rejectsRoomWithUnknownSchool() {
        when(schoolMapper.selectById(any())).thenReturn(null);
        EducationCalendarForms.Room form = new EducationCalendarForms.Room(
                null, 404L, "R-01", "录播教室", "RECORD", 0, null);

        assertThatThrownBy(() -> service.saveRoom(form)).isInstanceOf(BusinessException.class);
        verify(roomMapper, never()).insert(any(EduRoomPo.class));
    }

    @Test
    @DisplayName("数字校园同步来的教室不允许在管理端改")
    void rejectsEditingSyncedRoom() {
        when(schoolMapper.selectById(any())).thenReturn(new EduSchoolPo());
        EduRoomPo existing = new EduRoomPo();
        existing.setId(7L);
        existing.setSourceSystem("DIGITAL_CAMPUS");
        when(roomMapper.selectById(7L)).thenReturn(existing);

        EducationCalendarForms.Room form = new EducationCalendarForms.Room(
                7L, 1L, "R-01", "录播教室", "RECORD", 0, null);

        assertThatThrownBy(() -> service.saveRoom(form)).isInstanceOf(BusinessException.class);
        verify(roomMapper, never()).updateById(any(EduRoomPo.class));
    }

    // 以下三条覆盖合并教育主数据返工线时并进来的删除能力。

    @Test
    @DisplayName("删除学期时不存在的 ID 直接跳过，保持幂等")
    void deleteSemesterSkipsMissingIds() {
        when(semesterMapper.selectById(1L)).thenReturn(new EduSemesterPo());
        when(semesterMapper.selectById(2L)).thenReturn(null);
        when(semesterMapper.deleteById(1L)).thenReturn(1);

        assertThat(service.deleteSemesters(java.util.List.of(1L, 2L, 1L))).isEqualTo(1);
        verify(semesterMapper, never()).deleteById(2L);
    }

    @Test
    @DisplayName("数字校园同步来的教室不允许在管理端删")
    void rejectsDeletingSyncedRoom() {
        EduRoomPo synced = new EduRoomPo();
        synced.setId(7L);
        synced.setSourceSystem("DIGITAL_CAMPUS");
        when(roomMapper.selectById(7L)).thenReturn(synced);

        assertThatThrownBy(() -> service.deleteRooms(java.util.List.of(7L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数字校园");
        verify(roomMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("教室下还挂着设备时先拦下，不留孤儿设备")
    void rejectsDeletingRoomWithDevices() {
        EduRoomPo local = new EduRoomPo();
        local.setId(8L);
        local.setSourceSystem("HAN");
        when(roomMapper.selectById(8L)).thenReturn(local);
        when(deviceMapper.selectCount(any())).thenReturn(3L);

        assertThatThrownBy(() -> service.deleteRooms(java.util.List.of(8L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("设备");
        verify(roomMapper, never()).deleteById(any(Long.class));
    }
}
