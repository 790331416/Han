package com.han.system.sdfz.education;

import com.han.api.system.AuthServiceClient;
import com.han.api.system.domain.SessionRevokeRequest;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.mapper.SysDictDataMapper;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduRegionPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EducationForms;
import com.han.system.sdfz.education.domain.EducationOrganizationForms;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduPersonSubjectMapper;
import com.han.system.sdfz.education.mapper.EduRegionMapper;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学校/教育组织停用时撤销该校全部已绑定教育身份的会话（第三轮返工 E 节）。
 *
 * <p>覆盖 {@link EducationMasterDataService#saveSchool} 与
 * {@link EducationOrganizationTreeService#save} 两条写入路径：停用才撤销，改名/启用不撤销，
 * 撤销失败时学校状态更新随事务回滚。</p>
 */
@ExtendWith(MockitoExtension.class)
class EducationSchoolDisableRevokeTest {

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
    private EducationOrganizationTreeService orgTreeService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(2L).tenantId(1L).build());
        lenient().when(dataScopeService.current()).thenReturn(EducationDataScopeService.Scope.tenantWide());
        EduRegionPo region = new EduRegionPo();
        region.setId(51L);
        region.setRegionCode("500100");
        region.setStatus(0);
        lenient().when(regionMapper.selectOne(any())).thenReturn(region);
        // 会话撤销默认成功：个别用例再按需覆盖为 R.fail / 抛网络异常。
        lenient().when(authServiceClient.revokeSession(any())).thenReturn(R.ok());
        service = new EducationMasterDataService(schoolMapper, classMapper, personMapper, subjectMapper,
                deviceMapper, roomMapper, personClassMapper, personSubjectMapper, dictDataMapper, regionMapper,
                dataScopeService, authServiceClient);
        orgTreeService = new EducationOrganizationTreeService(schoolMapper, regionMapper, personMapper,
                dataScopeService, authServiceClient);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void disablingSchoolRevokesEveryBoundIdentity() {
        EduSchoolPo school = school(11L, 0, "S001");
        when(schoolMapper.selectById(11L)).thenReturn(school);
        when(personMapper.selectList(any())).thenReturn(List.of(
                person(5001L, 11L, 100L), person(5002L, 11L, 200L)));

        service.saveSchool(new EducationForms.School(11L, null, "S001", "School One", "MAIN", "500100", 1, null));

        ArgumentCaptor<SessionRevokeRequest> captor = ArgumentCaptor.forClass(SessionRevokeRequest.class);
        verify(authServiceClient, times(2)).revokeSession(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(SessionRevokeRequest::getIdentityId)
                .containsExactlyInAnyOrder(5001L, 5002L);
        assertThat(captor.getAllValues())
                .extracting(SessionRevokeRequest::getUserId)
                .containsExactlyInAnyOrder(100L, 200L);
        ArgumentCaptor<EduSchoolPo> schoolCaptor = ArgumentCaptor.forClass(EduSchoolPo.class);
        verify(schoolMapper).updateById(schoolCaptor.capture());
        assertThat(schoolCaptor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void renamingSchoolDoesNotRevokeSessions() {
        EduSchoolPo school = school(11L, 0, "S001");
        when(schoolMapper.selectById(11L)).thenReturn(school);

        service.saveSchool(new EducationForms.School(11L, null, "S001", "Renamed School", "MAIN", "500100", 0, null));

        verify(authServiceClient, never()).revokeSession(any(SessionRevokeRequest.class));
        verify(schoolMapper).updateById(school);
    }

    @Test
    void enablingSchoolDoesNotRevokeSessions() {
        EduSchoolPo school = school(11L, 1, "S001");
        when(schoolMapper.selectById(11L)).thenReturn(school);

        service.saveSchool(new EducationForms.School(11L, null, "S001", "School One", "MAIN", "500100", 0, null));

        verify(authServiceClient, never()).revokeSession(any(SessionRevokeRequest.class));
        verify(schoolMapper).updateById(school);
    }

    @Test
    void schoolStatusUpdateRollsBackWhenRevokeFails() {
        EduSchoolPo school = school(11L, 0, "S001");
        when(schoolMapper.selectById(11L)).thenReturn(school);
        when(personMapper.selectList(any())).thenReturn(List.of(person(5003L, 11L, 300L)));
        when(authServiceClient.revokeSession(any(SessionRevokeRequest.class)))
                .thenThrow(new RuntimeException("auth down"));

        assertThatThrownBy(() -> service.saveSchool(
                new EducationForms.School(11L, null, "S001", "School One", "MAIN", "500100", 1, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("会话撤销失败");
        verify(schoolMapper, never()).updateById(any(EduSchoolPo.class));
    }

    /** 学校停用撤销返回 R.fail（非成功 code）：抛业务异常，学校 updateById 未调用。 */
    @Test
    void revokeBusinessFailureRollsBackSchoolDisable() {
        EduSchoolPo school = school(11L, 0, "S001");
        when(schoolMapper.selectById(11L)).thenReturn(school);
        when(personMapper.selectList(any())).thenReturn(List.of(person(5004L, 11L, 400L)));
        when(authServiceClient.revokeSession(any(SessionRevokeRequest.class)))
                .thenReturn(R.fail(500, "auth down"));

        assertThatThrownBy(() -> service.saveSchool(
                new EducationForms.School(11L, null, "S001", "School One", "MAIN", "500100", 1, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("会话撤销失败");
        verify(schoolMapper, never()).updateById(any(EduSchoolPo.class));
    }

    @Test
    void organizationTreeSchoolDisableAlsoRevokesSessions() {
        EduSchoolPo org = school(20L, 0, "ORG_20");
        org.setOrgType("SCHOOL");
        org.setRegionId(1L);
        when(schoolMapper.selectById(20L)).thenReturn(org);
        EduRegionPo region = new EduRegionPo();
        region.setId(1L);
        region.setRegionCode("500100");
        region.setStatus(0);
        when(regionMapper.selectById(1L)).thenReturn(region);
        when(schoolMapper.selectList(any())).thenReturn(List.of());
        when(personMapper.selectList(any())).thenReturn(List.of(
                person(6001L, 20L, 100L), person(6002L, 20L, 200L)));

        orgTreeService.save(new EducationOrganizationForms.Organization(
                20L, null, "两江中学", "SCHOOL", "INDEPENDENT", "3", 1L, 1, 1, null));

        ArgumentCaptor<SessionRevokeRequest> captor = ArgumentCaptor.forClass(SessionRevokeRequest.class);
        verify(authServiceClient, times(2)).revokeSession(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(SessionRevokeRequest::getIdentityId)
                .containsExactlyInAnyOrder(6001L, 6002L);
        ArgumentCaptor<EduSchoolPo> schoolCaptor = ArgumentCaptor.forClass(EduSchoolPo.class);
        verify(schoolMapper).updateById(schoolCaptor.capture());
        assertThat(schoolCaptor.getValue().getStatus()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- 工具

    private static EduSchoolPo school(Long id, Integer status, String code) {
        EduSchoolPo value = new EduSchoolPo();
        value.setId(id);
        value.setStatus(status);
        value.setSchoolCode(code);
        value.setSourceSystem("HAN");
        value.setOrgType("SCHOOL");
        return value;
    }

    private static EduPersonPo person(Long id, Long schoolId, Long userId) {
        EduPersonPo value = new EduPersonPo();
        value.setId(id);
        value.setTenantId(1L);
        value.setSchoolId(schoolId);
        value.setUserId(userId);
        value.setSourceSystem("HAN");
        return value;
    }
}
