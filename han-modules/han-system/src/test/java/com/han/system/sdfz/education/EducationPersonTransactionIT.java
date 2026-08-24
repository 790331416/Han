package com.han.system.sdfz.education;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.han.common.core.exception.BusinessException;
import com.han.common.mybatis.handler.HanMetaObjectHandler;
import com.han.common.security.context.SecurityContextAdapter;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.domain.po.SysUserPo;
import com.han.system.mapper.SysDictDataMapper;
import com.han.system.mapper.SysRoleMapper;
import com.han.system.mapper.SysUserMapper;
import com.han.system.mapper.SysUserRoleMapper;
import com.han.system.sdfz.education.domain.EduPersonClassPo;
import com.han.system.sdfz.education.domain.EducationForms;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduPersonSubjectMapper;
import com.han.system.sdfz.education.mapper.EduRegionMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduSubjectMapper;
import com.han.system.sdfz.education.mapper.EduUserScopeMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 人员统一入口的事务与并发集成测试（真实 MySQL + 真实事务管理器）。
 *
 * <p>Mockito 单测只能证明"抛异常前没调用 insert"这一执行顺序，证明不了"抛异常后已写入的被撤销"。
 * 验收方案 §4.14 要求的恰恰是后者，因此这些用例必须跑在真实事务边界上。</p>
 *
 * <p>运行方式（需要一个空库，脚本会在每个用例前重建表）：</p>
 * <pre>
 * $env:HAN_IT_MYSQL_URL='jdbc:mysql://10.18.35.95:23306/han_edu_it?...'
 * $env:HAN_IT_MYSQL_USER='...'; $env:HAN_IT_MYSQL_PASSWORD='...'
 * mvn -pl han-modules/han-system test -Dtest=EducationPersonTransactionIT
 * </pre>
 * 未设置 {@code HAN_IT_MYSQL_URL} 时整类跳过，不影响常规构建。
 */
@EnabledIfEnvironmentVariable(named = "HAN_IT_MYSQL_URL", matches = ".+")
@SpringJUnitConfig(EducationPersonTransactionIT.ItConfig.class)
class EducationPersonTransactionIT {

    private static final Long SCHOOL_ID = 900001L;
    private static final Long CLASS_1 = 900011L;
    private static final Long CLASS_2 = 900012L;
    private static final Long TEACHER_ROLE = 202608120101L;
    private static final String OVERLONG_NAME = "超".repeat(200);

    @Autowired
    private EducationPersonService personService;
    @Autowired
    private SysUserMapper userMapper;
    @Autowired
    private SysUserRoleMapper userRoleMapper;
    @Autowired
    private EduPersonMapper personMapper;
    @Autowired
    private EduPersonClassMapper personClassMapper;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("sdfz/it-schema-mysql.sql"));
        }
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(1L).build());
    }

    /** §4.14：创建账号后创建人员失败 → 整笔回滚，sys_user 不留半成品。 */
    @Test
    void rollsBackAccountWhenPersonInsertFails() {
        EducationForms.Person form = teacher("IT-T-001", OVERLONG_NAME, "it.rollback", List.of(TEACHER_ROLE), null);

        assertThatThrownBy(() -> txGet(() -> personService.save(form)))
                .as("失败必须发生在人员写入这一步，而不是更早的校验")
                .hasMessageContaining("person_name");

        assertThat(countUsers("it.rollback")).as("账号必须随人员写入失败一起回滚").isZero();
        assertThat(userRoleMapper.selectCount(null)).as("角色关系也必须回滚").isZero();
        assertThat(personMapper.selectCount(null)).as("不得留下半成品人员").isZero();
    }

    /** §4.14：人员写入后的后续步骤失败 → 账号、人员、关系一并回滚。 */
    @Test
    void rollsBackEverythingWhenMembershipStepFails() {
        EducationForms.Person form = teacher("IT-T-002", "张老师", "it.membership",
                List.of(TEACHER_ROLE), List.of(999999999L));

        assertThatThrownBy(() -> txGet(() -> personService.save(form)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("班级不存在");

        assertThat(countUsers("it.membership")).isZero();
        assertThat(userRoleMapper.selectCount(null)).isZero();
        assertThat(personMapper.selectCount(null)).isZero();
        assertThat(personClassMapper.selectCount(null)).isZero();
    }

    /** §4.14：两个管理员并发创建同一人员 → 最多一个成功，失败方拿到可识别冲突。 */
    @Test
    void concurrentCreateOfSamePersonKeepsExactlyOneRow() throws Exception {
        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        Callable<Void> attempt = () -> {
            SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(1L).build());
            ready.countDown();
            fire.await(10, TimeUnit.SECONDS);
            try {
                txGet(() -> personService.save(
                        teacher("IT-T-RACE", "并发老师", "it.race" + Thread.currentThread().threadId(), null, null)));
                success.incrementAndGet();
            } catch (Exception e) {
                if (isIdentifiableConflict(e)) {
                    conflict.incrementAndGet();
                }
            } finally {
                SecurityContextHolder.clear();
            }
            return null;
        };

        Future<Void> first = pool.submit(attempt);
        Future<Void> second = pool.submit(attempt);
        ready.await(10, TimeUnit.SECONDS);
        fire.countDown();
        first.get(30, TimeUnit.SECONDS);
        second.get(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(success.get()).as("最多一个成功").isEqualTo(1);
        assertThat(conflict.get()).as("失败方必须收到可识别冲突而不是未知错误").isEqualTo(1);
        assertThat(personMapper.selectCount(null)).as("不得出现两条同工号人员").isEqualTo(1);
    }

    /** 逻辑删除后用同工号重建：验证 active_person_no 生成列唯一索引真的把墓碑行排除了。 */
    @Test
    void allowsRecreatingWithSameBusinessCodeAfterLogicalDelete() throws Exception {
        Long personId = txGet(() -> personService.save(
                teacher("IT-T-003", "李老师", "it.recreate", null, null))).personId();
        txGet(() -> personService.deletePeople(List.of(personId)));

        EducationForms.PersonResult again = txGet(() -> personService.save(
                teacher("IT-T-003", "李老师二", "it.recreate2", null, null)));

        assertThat(again.personId()).isNotNull().isNotEqualTo(personId);
        assertThat(rawPersonNo(personId)).as("墓碑行保留，工号保持原值不被改写").isEqualTo("IT-T-003");
    }

    /**
     * 岗位真的落到了 edu_person.duty_code 这一列上。
     *
     * <p>Mockito 单测只能证明 service 调了 {@code setDutyCode}，证明不了这个字段有没有列可落——
     * {@code dutyCode} 走的是 MyBatis 驼峰自动映射，没有 {@code @TableField} 兜底，
     * 列名写错或迁移脚本没执行时，单测照样全绿，直到线上读出来永远是 null。
     * 因此这里既读回 PO，也直接读原始列。
     */
    @Test
    void persistsDutyCodeIntoItsOwnColumn() throws Exception {
        Long adminId = txGet(() -> personService.save(
                teacher("IT-T-DUTY-1", "管理岗老师", "it.duty.admin", null, null, "SCHOOL_ADMIN"))).personId();
        Long plainId = txGet(() -> personService.save(
                teacher("IT-T-DUTY-2", "普通老师", "it.duty.plain", null, null, null))).personId();

        assertThat(personMapper.selectById(adminId).getDutyCode())
                .as("显式授予的校级管理岗必须读得回来").isEqualTo("SCHOOL_ADMIN");
        assertThat(rawDutyCode(adminId))
                .as("必须落在 duty_code 列上，而不是只活在 Java 对象里").isEqualTo("SCHOOL_ADMIN");
        assertThat(rawDutyCode(plainId))
                .as("没选岗位就是普通教师，绝不能默认成管理岗").isEqualTo("TEACHER");
    }

    /** 转班 1 → 2 → 1：验证关系表墓碑行不再占用唯一键。 */
    @Test
    void allowsMovingBackToPreviousClass() {
        Long personId = txGet(() -> personService.save(
                student("IT-S-001", "王同学", List.of(CLASS_1)))).personId();

        txGet(() -> personService.replaceMemberships(
                new EducationForms.Membership(personId, List.of(CLASS_2), null)));
        txGet(() -> personService.replaceMemberships(
                new EducationForms.Membership(personId, List.of(CLASS_1), null)));

        List<EduPersonClassPo> active = personService.listMemberships(personId);
        assertThat(active).as("转回原班后只保留一条有效归班关系").hasSize(1);
        assertThat(active.getFirst().getClassId()).isEqualTo(CLASS_1);
    }

    // ---------------------------------------------------------------- 工具

    private <T> T txGet(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private long countUsers(String username) {
        Long value = userMapper.selectCount(new LambdaQueryWrapper<SysUserPo>()
                .likeRight(SysUserPo::getUsername, username));
        return value == null ? 0L : value;
    }

    /** 绕开逻辑删除过滤直接读墓碑行，验证业务编码没有被改写。 */
    private String rawPersonNo(Long personId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT person_no FROM edu_person WHERE id = ?")) {
            statement.setLong(1, personId);
            try (var rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String rawDutyCode(Long personId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT duty_code FROM edu_person WHERE id = ?")) {
            statement.setLong(1, personId);
            try (var rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static boolean isIdentifiableConflict(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof com.han.common.core.exception.ConflictException) {
                return true;
            }
            if (current instanceof java.sql.SQLIntegrityConstraintViolationException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    private static EducationForms.Person teacher(String personNo, String name, String username,
                                                 List<Long> roleIds, List<Long> classIds) {
        return teacher(personNo, name, username, roleIds, classIds, null);
    }

    private static EducationForms.Person teacher(String personNo, String name, String username,
                                                 List<Long> roleIds, List<Long> classIds, String dutyCode) {
        return new EducationForms.Person(null, SCHOOL_ID, personNo, name, "TEACHER",
                dutyCode, phoneFor(personNo), 0, null, null, true, username, "Teacher@2026", roleIds, null, classIds, null, null);
    }

    private static EducationForms.Person student(String personNo, String name, List<Long> classIds) {
        return new EducationForms.Person(null, SCHOOL_ID, personNo, name, "STUDENT",
                null, phoneFor(personNo), 0, null, null, false, null, null, null, null, classIds, null, null);
    }

    private static String phoneFor(String personNo) {
        return "139" + String.format("%08d", Math.abs(personNo.hashCode()) % 100_000_000);
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackages = {"com.han.system.sdfz.education.mapper", "com.han.system.mapper"})
    static class ItConfig {

        @Bean
        DataSource dataSource() {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(System.getenv("HAN_IT_MYSQL_URL"));
            ds.setUsername(System.getenv("HAN_IT_MYSQL_USER"));
            ds.setPassword(System.getenv("HAN_IT_MYSQL_PASSWORD"));
            ds.setMaximumPoolSize(8);
            return ds;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);

            GlobalConfig globalConfig = new GlobalConfig();
            GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
            dbConfig.setLogicDeleteField("delFlag");
            dbConfig.setLogicDeleteValue("1");
            dbConfig.setLogicNotDeleteValue("0");
            globalConfig.setDbConfig(dbConfig);
            globalConfig.setBanner(false);
            // 与运行时一致地填充 createTime / tenantId / createBy 等审计字段，
            // 否则 create_time 会以 NULL 入库，掩盖被测的真实失败原因。
            globalConfig.setMetaObjectHandler(new HanMetaObjectHandler(new SecurityContextAdapter()));
            factory.setGlobalConfig(globalConfig);

            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);

            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
            factory.setPlugins(interceptor);

            factory.setMapperLocations(new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                    .getResources("classpath*:mapper/**/*.xml"));
            return factory.getObject();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        EducationDataScopeService educationDataScopeService(EduUserScopeMapper userScopeMapper,
                                                            EduSchoolMapper schoolMapper,
                                                            EduRegionMapper regionMapper) {
        return new EducationDataScopeService(userScopeMapper, schoolMapper);
        }

        @Bean
        EducationPersonService educationPersonService(EduPersonMapper personMapper,
                                                      EduPersonClassMapper personClassMapper,
                                                      EduPersonSubjectMapper personSubjectMapper,
                                                      EduSchoolMapper schoolMapper,
                                                      EduClassMapper classMapper,
                                                      EduSubjectMapper subjectMapper,
                                                      SysUserMapper userMapper,
                                                      SysUserRoleMapper userRoleMapper,
                                                      SysRoleMapper roleMapper,
                                                      SysDictDataMapper dictDataMapper,
                                                      EducationDataScopeService dataScopeService) {
        return new EducationPersonService(personMapper, personClassMapper, personSubjectMapper,
                schoolMapper, classMapper, subjectMapper, userMapper, userRoleMapper, roleMapper, dictDataMapper,
                dataScopeService, new EducationAccountIdentityService(personMapper));
        }
    }
}
