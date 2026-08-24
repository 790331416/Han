package com.han.system.sdfz.compat;

import com.han.api.system.domain.ClassroomIdentityVO;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.ClassroomTokenCodec;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.mapper.EduAcademicYearMapper;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduSemesterMapper;
import com.han.system.sdfz.education.mapper.EduSubjectMapper;
import com.han.system.mapper.SysDictDataMapper;
import com.han.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 一账号多学校身份下的课堂凭证与目录身份隔离（Phase 2 第五批）。
 *
 * <p>钉五件事：多身份未传 identityId 报错、单身份自动选择、Active Key 按身份区分、
 * 目录身份归属校验、会话撤销后旧凭证失效并换发新凭证。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LegacyClassroomIdentityTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Mock
    private LegacyDirectoryService directoryService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private EduPersonMapper personMapper;

    /** 用内存 map 当 Redis：键的生灭是身份隔离用例的判定依据，不能用 mock 糊过去。 */
    private final Map<String, String> store = new HashMap<>();
    private final Set<String> keys = new HashSet<>();

    @BeforeAll
    static void bootstrapEntityMetadata() {
        LegacyTableInfoBootstrap.init();
    }

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            keys.add(inv.getArgument(0));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(valueOperations.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0)));
        when(redisTemplate.hasKey(anyString())).thenAnswer(inv -> keys.contains(inv.getArgument(0)));
    }

    // ------------------------------------------------------------ 身份选择

    @Test
    void multiIdentityWithoutExplicitSelectionRaisesBusinessError() {
        when(directoryService.personsByUserId(100L)).thenReturn(List.of(
                person(11L, 100L, LegacyDirectoryService.TEACHER),
                person(21L, 100L, LegacyDirectoryService.STUDENT)));
        when(directoryService.schoolById(7L)).thenReturn(school());
        when(directoryService.externalUserId(any())).thenReturn("100");
        when(directoryService.classesOf(any())).thenReturn(List.of());

        LegacyClassroomIdentityService service = new LegacyClassroomIdentityService(properties(), directoryService);

        assertThatThrownBy(() -> service.resolve(100L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前账号存在多个教育身份，请先选择身份");
        assertThatThrownBy(() -> service.resolve(100L, ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前账号存在多个教育身份，请先选择身份");
        // 显式选择后按指定身份返回，而不是默认取第一条。
        assertThat(service.resolve(100L, "21").getIdentityId()).isEqualTo("21");
    }

    @Test
    void singleIdentityIsAutoSelectedForBackwardCompatibility() {
        when(directoryService.personsByUserId(100L)).thenReturn(List.of(person(11L, 100L, LegacyDirectoryService.TEACHER)));
        when(directoryService.schoolById(7L)).thenReturn(school());
        when(directoryService.externalUserId(any())).thenReturn("100");
        when(directoryService.classesOf(any())).thenReturn(List.of());

        LegacyClassroomIdentityService service = new LegacyClassroomIdentityService(properties(), directoryService);

        assertThat(service.resolve(100L, null).getIdentityId()).isEqualTo("11");
        assertThat(service.resolve(100L).getIdentityId()).isEqualTo("11");
    }

    // ------------------------------------------------------------ Active Key 身份粒度

    @Test
    void classroomActiveKeyIsScopedByIdentityId() {
        LegacyTokenIssuer issuer = new LegacyTokenIssuer(properties(), redisTemplate);
        EduPersonPo identityA = person(11L, 100L, LegacyDirectoryService.TEACHER);
        EduPersonPo identityB = person(21L, 100L, LegacyDirectoryService.TEACHER);

        String tokenA = issuer.issueSession(identityA).token();
        String tokenB = issuer.issueSession(identityB).token();

        assertThat(tokenA)
                .as("同一账号的不同身份必须各持一张凭证，不能复用导致 identityId 错位")
                .isNotEqualTo(tokenB);
        assertThat(store)
                .containsKey("sdfz:classroom:active:100:11")
                .containsKey("sdfz:classroom:active:100:21");
    }

    // ------------------------------------------------------------ 目录身份归属

    @Test
    void directoryIdentityRejectsAnotherIdentityOfTheSameAccount() {
        LegacyDirectoryService service = directoryServiceUnderTest();
        when(personMapper.selectOne(any())).thenReturn(person(21L, 100L, LegacyDirectoryService.TEACHER));

        LegacyRequest request = new LegacyRequest(LegacyProtocol.Consumer.LEGACY_API, null, "test",
                Map.of("pkId", "21"), new LegacyRequest.Scope(1L, 7L, 11L, 100L));

        assertThat(asMap(service.identity(request).value()))
                .as("同账号的另一个身份不能被当前 token 读取，只校验 schoolId 不够")
                .isEmpty();
    }

    // ------------------------------------------------------------ 切换/登出后旧凭证失效

    @Test
    void revokedSessionForcesFreshTokenInsteadOfReusingTheInvalidatedOne() {
        LegacyTokenIssuer issuer = new LegacyTokenIssuer(properties(), redisTemplate);
        EduPersonPo identityA = person(11L, 100L, LegacyDirectoryService.TEACHER);

        String first = issuer.issueSession(identityA).token();
        String tokenId = ClassroomTokenCodec.verify(first, SECRET, Instant.now().getEpochSecond()).tokenId();

        // 等同登出/切换身份的撤销链：删掉会话键，旧凭证即失效。
        keys.remove(ClassroomTokenCodec.SESSION_KEY_PREFIX + tokenId);

        String reissued = issuer.issueSession(identityA).token();
        assertThat(reissued)
                .as("会话被撤销后必须换发新凭证，不能把已作废的凭证再发一遍")
                .isNotEqualTo(first);
    }

    // ------------------------------------------------------------ 禁止回退第一条

    @Test
    void selectPrimaryRaisesIdentityExpiredWhenTheDeclaredIdentityIsGone() {
        EduPersonPo teacher = person(11L, 100L, LegacyDirectoryService.TEACHER);
        EduPersonPo student = person(21L, 100L, LegacyDirectoryService.STUDENT);
        LegacyCredentialService service = credentialService();
        String interim = new LegacyTokenIssuer(properties(), redisTemplate).issueInterim(teacher).token();
        // C2 签发时身份是 11（教师），到 C3 时该身份已被解绑/停用/离校，只剩学生 21。
        when(directoryService.personsByUserId(100L)).thenReturn(List.of(student));
        when(directoryService.roleOf(student)).thenReturn(Map.of("roleType", 4, "userId", "100"));
        when(directoryService.externalUserId(student)).thenReturn("100");

        LegacyRequest request = new LegacyRequest(LegacyProtocol.Consumer.LEGACY_UI, interim,
                LegacyPaths.UI_GET_ONE_BY_ID, Map.of());

        assertThatThrownBy(() -> service.currentUser(request))
                .as("声明了 identityId 却找不到对应身份，必须报身份失效，不能回退到列表第一条")
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前身份已失效，请重新登录");
    }

    @Test
    void legacyCredentialWithoutIdentityIdFallsBackOnlyForASingleIdentity() {
        LegacyCredentialService service = credentialService();
        EduPersonPo teacher = person(11L, 100L, LegacyDirectoryService.TEACHER);
        String legacy = legacyTokenWithoutIdentityId();
        when(directoryService.personsByUserId(100L)).thenReturn(List.of(teacher));
        when(directoryService.roleOf(teacher)).thenReturn(Map.of("roleType", 2, "userId", "100"));
        when(directoryService.externalUserId(teacher)).thenReturn("100");

        Map<String, Object> result = asMap(service.currentUser(
                new LegacyRequest(LegacyProtocol.Consumer.LEGACY_UI, legacy,
                        LegacyPaths.UI_GET_ONE_BY_ID, Map.of())).value());

        assertThat(result.get("accessToken")).isInstanceOf(String.class);
    }

    @Test
    void legacyCredentialWithoutIdentityIdRejectsMultipleIdentities() {
        LegacyCredentialService service = credentialService();
        String legacy = legacyTokenWithoutIdentityId();
        when(directoryService.personsByUserId(100L)).thenReturn(List.of(
                person(11L, 100L, LegacyDirectoryService.TEACHER),
                person(21L, 100L, LegacyDirectoryService.TEACHER)));

        assertThatThrownBy(() -> service.currentUser(
                new LegacyRequest(LegacyProtocol.Consumer.LEGACY_UI, legacy,
                        LegacyPaths.UI_GET_ONE_BY_ID, Map.of())))
                .as("旧凭证未声明身份但账号有多个有效身份时，不得默认取第一条，必须要求重新登录")
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前账号存在多个教育身份，请重新登录");
    }

    // ------------------------------------------------------------ 目录当前身份必须命中 Token identityId

    @Test
    void directoryIdentityRejectsAnotherAccountInTheSameSchool() {
        LegacyDirectoryService service = directoryServiceUnderTest();
        // 同校（school 7）的另一个账号身份（userId 200），token 绑定的是 identityId=11 / userId=100。
        when(personMapper.selectOne(any())).thenReturn(person(21L, 200L, LegacyDirectoryService.TEACHER));

        LegacyRequest request = new LegacyRequest(LegacyProtocol.Consumer.LEGACY_API, null, "test",
                Map.of("pkId", "21"), new LegacyRequest.Scope(1L, 7L, 11L, 100L));

        assertThat(asMap(service.identity(request).value()))
                .as("同校其他账号的身份同样不能被当前 token 读取，只校验 schoolId 不够")
                .isEmpty();
    }

    // ------------------------------------------------------------ 学校状态

    @Test
    void identitiesAreHiddenWhenTheSchoolIsMissing() {
        LegacyClassroomIdentityService service = identityService();
        when(directoryService.personsByUserId(100L)).thenReturn(List.of(
                person(11L, 100L, LegacyDirectoryService.TEACHER)));
        when(directoryService.schoolById(7L)).thenReturn(null);

        assertThat(service.list(100L))
                .as("学校不存在时不得返回身份列表，也不允许选择/签发课堂 Token")
                .isEmpty();
        assertThat(service.resolve(100L)).isNull();
        assertThat(service.resolve(100L, "11")).isNull();
    }

    @Test
    void identitiesAreHiddenWhenTheSchoolIsDisabledOrDeletedOrTenantMismatched() {
        LegacyClassroomIdentityService service = identityService();
        when(directoryService.personsByUserId(100L)).thenReturn(List.of(
                person(11L, 100L, LegacyDirectoryService.TEACHER)));

        when(directoryService.schoolById(7L)).thenReturn(school(7L, 1, 0, null));
        assertThat(service.list(100L)).as("学校停用不返回身份").isEmpty();

        when(directoryService.schoolById(7L)).thenReturn(school(7L, 0, 1, null));
        assertThat(service.list(100L)).as("学校删除不返回身份").isEmpty();

        when(directoryService.schoolById(7L)).thenReturn(school(7L, 0, 0, 2L));
        assertThat(service.list(100L)).as("人员与学校租户不一致不返回身份").isEmpty();
    }

    @Test
    void identitiesRemainVisibleWhenTheSchoolIsActive() {
        LegacyClassroomIdentityService service = identityService();
        when(directoryService.personsByUserId(100L)).thenReturn(List.of(
                person(11L, 100L, LegacyDirectoryService.TEACHER)));
        when(directoryService.schoolById(7L)).thenReturn(school());
        when(directoryService.externalUserId(any())).thenReturn("100");
        when(directoryService.classesOf(any())).thenReturn(List.of());

        assertThat(service.list(100L))
                .extracting(ClassroomIdentityVO::getIdentityId)
                .containsExactly("11");
    }

    // ------------------------------------------------------------ 管理端可用性

    @Test
    void managementAvailabilityRequiresSchoolAdminDutyAndManagementRole() {
        LegacyClassroomIdentityService service = identityService();
        EduPersonPo plain = person(11L, 100L, LegacyDirectoryService.TEACHER);
        EduPersonPo admin = person(12L, 100L, LegacyDirectoryService.TEACHER);
        admin.setDutyCode("SCHOOL_ADMIN");
        when(directoryService.personsByUserId(100L)).thenReturn(List.of(plain, admin));
        when(directoryService.schoolById(7L)).thenReturn(school());
        when(directoryService.externalUserId(any())).thenReturn("100");
        when(directoryService.classesOf(any())).thenReturn(List.of());
        when(directoryService.hasManagementRole(100L)).thenReturn(true);

        List<ClassroomIdentityVO> identities = service.list(100L);

        assertThat(identities).extracting(ClassroomIdentityVO::getIdentityId)
                .containsExactly("11", "12");
        assertThat(identities).extracting(ClassroomIdentityVO::isManagementAvailable)
                .containsExactly(false, true);
        assertThat(identities).extracting(ClassroomIdentityVO::getManagementUnavailableReason)
                .containsExactly("当前岗位未开通管理端", "");
    }

    @Test
    void schoolAdminWithoutManagementRoleIsNotManagementAvailable() {
        LegacyClassroomIdentityService service = identityService();
        EduPersonPo admin = person(12L, 100L, LegacyDirectoryService.TEACHER);
        admin.setDutyCode("SCHOOL_ADMIN");
        when(directoryService.personsByUserId(100L)).thenReturn(List.of(admin));
        when(directoryService.schoolById(7L)).thenReturn(school());
        when(directoryService.externalUserId(any())).thenReturn("100");
        when(directoryService.classesOf(any())).thenReturn(List.of());
        when(directoryService.hasManagementRole(100L)).thenReturn(false);

        ClassroomIdentityVO identity = service.list(100L).getFirst();

        assertThat(identity.isManagementAvailable()).isFalse();
        assertThat(identity.getManagementUnavailableReason()).isEqualTo("账号未配置管理端角色");
    }

    // ------------------------------------------------------------ 夹具

    private LegacyDirectoryService directoryServiceUnderTest() {
        return new LegacyDirectoryService(properties(),
                mock(EduSchoolMapper.class), mock(EduClassMapper.class), mock(EduAcademicYearMapper.class),
                mock(EduSemesterMapper.class), personMapper, mock(EduPersonClassMapper.class),
                mock(EduDeviceMapper.class), mock(EduRoomMapper.class), mock(SysUserMapper.class),
                mock(SysDictDataMapper.class), mock(EduSubjectMapper.class));
    }

    private static LegacyCompatProperties properties() {
        LegacyCompatProperties properties = new LegacyCompatProperties();
        properties.setEnabled(true);
        properties.setTenantId(1L);
        properties.setTokenSecret(SECRET);
        properties.setTokenTtlSeconds(3600L);
        return properties;
    }

    private LegacyCredentialService credentialService() {
        LegacyCompatProperties props = properties();
        LegacyTokenIssuer issuer = new LegacyTokenIssuer(props, redisTemplate);
        return new LegacyCredentialService(props, directoryService, issuer, redisTemplate, new LegacyCipher(props));
    }

    private LegacyClassroomIdentityService identityService() {
        return new LegacyClassroomIdentityService(properties(), directoryService);
    }

    /** 构造一张旧版「未声明 identityId」的兼容凭证，并把会话键写入内存 Redis。 */
    private String legacyTokenWithoutIdentityId() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", "100");
        claims.put("username", "张老师");
        claims.put("userType", "USER");
        claims.put("roleType", "2");
        claims.put("roles", List.of("2", "TEACHER"));
        claims.put("status", 0);
        claims.put("schoolId", "7");
        claims.put("hanUserId", "100");
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String token = ClassroomTokenCodec.issue(claims, SECRET, Instant.now().getEpochSecond(), 900, tokenId);
        String sessionKey = ClassroomTokenCodec.SESSION_KEY_PREFIX + tokenId;
        store.put(sessionKey, "100");
        keys.add(sessionKey);
        return token;
    }

    private static EduPersonPo person(Long id, Long userId, String personType) {
        EduPersonPo person = new EduPersonPo();
        person.setId(id);
        person.setUserId(userId);
        person.setSchoolId(7L);
        person.setPersonName(personType + " 用户");
        person.setPersonType(personType);
        person.setStatus(0);
        person.setLeaveFlag(0);
        person.setDelFlag(0);
        return person;
    }

    private static EduSchoolPo school() {
        EduSchoolPo school = new EduSchoolPo();
        school.setId(7L);
        school.setSchoolName("巴蜀云校");
        school.setStatus(0);
        school.setDelFlag(0);
        return school;
    }

    private static EduSchoolPo school(Long id, Integer status, Integer delFlag, Long tenantId) {
        EduSchoolPo school = new EduSchoolPo();
        school.setId(id);
        school.setSchoolName("巴蜀云校");
        school.setStatus(status);
        school.setDelFlag(delFlag);
        school.setTenantId(tenantId);
        return school;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
