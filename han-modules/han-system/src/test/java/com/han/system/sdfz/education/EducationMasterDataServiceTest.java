package com.han.system.sdfz.education;

import com.han.api.system.AuthServiceClient;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduDevicePo;
import com.han.system.sdfz.education.domain.EduRegionPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduSubjectPo;
import com.han.system.sdfz.education.domain.EducationForms;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduPersonSubjectMapper;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduRegionMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduSubjectMapper;
import com.han.system.mapper.SysDictDataMapper;
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
import static org.mockito.Mockito.lenient;
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
    @Mock
    private SysDictDataMapper dictDataMapper;
    @Mock
    private EduRegionMapper regionMapper;
    @Mock
    private EducationDataScopeService dataScopeService;
    @Mock
    private AuthServiceClient authServiceClient;

    private EducationMasterDataService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(2L).tenantId(1L).build());
        lenient().when(dataScopeService.current()).thenReturn(EducationDataScopeService.Scope.tenantWide());
        EduRegionPo region = new EduRegionPo();
        region.setId(51L);
        region.setRegionCode("500100");
        region.setStatus(0);
        lenient().when(regionMapper.selectOne(any())).thenReturn(region);
        service = new EducationMasterDataService(schoolMapper, classMapper, personMapper, subjectMapper,
                deviceMapper, roomMapper, personClassMapper, personSubjectMapper, dictDataMapper, regionMapper, dataScopeService,
                authServiceClient);
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
                null, null, " S001 ", " School One ", "MAIN", "500100", 0, null));

        ArgumentCaptor<EduSchoolPo> captor = ArgumentCaptor.forClass(EduSchoolPo.class);
        verify(schoolMapper).insert(captor.capture());
        assertThat(id).isEqualTo(11L);
        assertThat(captor.getValue())
                .extracting(EduSchoolPo::getTenantId, EduSchoolPo::getSourceSystem,
                        EduSchoolPo::getSchoolCode, EduSchoolPo::getSchoolName, EduSchoolPo::getRegionId)
                .containsExactly(1L, "HAN", "SCHOOL_SCHOOL_ONE", "School One", 51L);
    }

    @Test
    void rejectsSchoolWhenGeneratedCodesAreExhausted() {
        when(schoolMapper.selectCount(any())).thenReturn(1L);

        EducationForms.School form = new EducationForms.School(
                null, null, "S001", "School One", "MAIN", "500100", 0, null);

        assertThatThrownBy(() -> service.saveSchool(form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同名编码过多");
        verify(schoolMapper, never()).insert(any(EduSchoolPo.class));
    }

    @Test
    void rejectsEditingDigitalCampusOwnedSchool() {
        EduSchoolPo existing = new EduSchoolPo();
        existing.setId(11L);
        existing.setSourceSystem("DIGITAL_CAMPUS");
        when(schoolMapper.selectById(11L)).thenReturn(existing);

        EducationForms.School form = new EducationForms.School(
                11L, null, "S001", "School One", "MAIN", "500100", 0, null);

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
    void rejectsCreatingClassUnderEducationBureau() {
        EduSchoolPo bureau = localSchool(7L);
        bureau.setOrgType("EDU_BUREAU");
        when(schoolMapper.selectById(7L)).thenReturn(bureau);

        assertThatThrownBy(() -> service.saveClass(new EducationForms.ClassInfo(
                null, 7L, null, null, "七年级一班", "NORMAL", 0, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能归属校区或独立学校");
        verify(classMapper, never()).insert(any(EduClassPo.class));
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

    @Test
    void generatesSubjectCodeFromSchoolAndSubjectName() {
        when(schoolMapper.selectById(7L)).thenReturn(localSchool(7L));
        when(subjectMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            EduSubjectPo value = invocation.getArgument(0);
            value.setId(31L);
            return 1;
        }).when(subjectMapper).insert(any(EduSubjectPo.class));

        Long id = service.saveSubject(new EducationForms.Subject(null, 7L, null, "语文", 0, 0, null));

        ArgumentCaptor<EduSubjectPo> captor = ArgumentCaptor.forClass(EduSubjectPo.class);
        verify(subjectMapper).insert(captor.capture());
        assertThat(id).isEqualTo(31L);
        assertThat(captor.getValue().getSubjectCode()).isEqualTo("SUBJECT_S001_YU_WEN");
    }

    @Test
    void persistsOnlyApplicationsMatchingTheSelectedDeviceType() {
        when(schoolMapper.selectById(7L)).thenReturn(localSchool(7L));
        when(deviceMapper.selectCount(any())).thenReturn(0L);
        when(dictDataMapper.selectCount(any())).thenReturn(1L);
        doAnswer(invocation -> {
            EduDevicePo value = invocation.getArgument(0);
            value.setId(41L);
            return 1;
        }).when(deviceMapper).insert(any(EduDevicePo.class));

        Long id = service.saveDevice(new EducationForms.Device(null, 7L, null, "DV001", "教室摄像机",
                "VIDEO_ANALYSIS", List.of("VIDEO_ANALYSIS:CAMPUS_MONITORING", "VIDEO_ANALYSIS:INTELLIGENT_PATROL"),
                null, null, "IN_USE", 0, null));

        ArgumentCaptor<EduDevicePo> captor = ArgumentCaptor.forClass(EduDevicePo.class);
        verify(deviceMapper).insert(captor.capture());
        assertThat(id).isEqualTo(41L);
        assertThat(captor.getValue()).extracting(EduDevicePo::getDeviceType, EduDevicePo::getApplicationTypes, EduDevicePo::getAssetStatus)
                .containsExactly("VIDEO_ANALYSIS", "VIDEO_ANALYSIS:CAMPUS_MONITORING,VIDEO_ANALYSIS:INTELLIGENT_PATROL", "IN_USE");
    }

    @Test
    void acceptsEnabledGlobalDeviceDictionariesWhenTenantDictionariesAreMissing() {
        when(schoolMapper.selectById(7L)).thenReturn(localSchool(7L));
        when(deviceMapper.selectCount(any())).thenReturn(0L);
        when(dictDataMapper.selectCount(any())).thenReturn(0L, 1L, 0L, 1L, 0L, 1L);
        doAnswer(invocation -> {
            EduDevicePo value = invocation.getArgument(0);
            value.setId(42L);
            return 1;
        }).when(deviceMapper).insert(any(EduDevicePo.class));

        Long id = service.saveDevice(new EducationForms.Device(null, 7L, null, "DV002", "录播设备",
                "RECORDER", List.of("RECORDER:LIVE"), null, null, "IN_USE", 0, null));

        assertThat(id).isEqualTo(42L);
    }

    @Test
    void rejectsMultipleRecorderApplications() {
        when(schoolMapper.selectById(7L)).thenReturn(localSchool(7L));
        when(deviceMapper.selectCount(any())).thenReturn(0L);
        when(dictDataMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.saveDevice(new EducationForms.Device(null, 7L, null, "DV002", "录播设备",
                "RECORDER", List.of("RECORDER:LIVE", "RECORDER:RECORD"), null, null, "IN_USE", 0, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能选择一个");
        verify(deviceMapper, never()).insert(any(EduDevicePo.class));
    }

    // 学期用例随 saveSemester 一并移交 EducationCalendarServiceTest：
    // 学期写入已由 EducationCalendarService 独占，本类不再有该路径。

    private static EduSchoolPo localSchool(Long id) {
        EduSchoolPo school = new EduSchoolPo();
        school.setId(id);
        school.setSourceSystem("HAN");
        school.setSchoolCode("S001");
        school.setOrgType("SCHOOL");
        school.setSchoolManageType("INDEPENDENT");
        return school;
    }
}
