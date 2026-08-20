package com.han.system.sdfz.order.support;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.han.common.core.context.SecurityContext;
import com.han.common.mybatis.config.TenantProperties;
import com.han.common.mybatis.handler.HanMetaObjectHandler;
import com.han.common.mybatis.handler.HanTenantLineHandler;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.common.security.context.SecurityContextAdapter;
import com.han.system.sdfz.education.EducationCalendarService;
import com.han.system.sdfz.order.CourseGrantLedger;
import com.han.system.sdfz.order.CourseGrantService;
import com.han.system.sdfz.order.CourseOrderService;
import com.han.system.sdfz.order.CourseOrderTenantScope;
import com.han.system.sdfz.order.legacy.LegacyClassroomJdbcGateway;
import com.han.system.sdfz.order.legacy.LegacyClassroomProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * 订购模块集成测试的上下文。
 *
 * <p>本机没有 Docker，用不了 Testcontainers，改用两个 H2 库分别模拟 Han 主库与三课堂旧库。
 * 关键是<b>事务管理器是真的</b>：{@link DataSourceTransactionManager} + {@link EnableTransactionManagement}，
 * 于是 {@code @Transactional} 的回滚、{@code REQUIRES_NEW} 的独立提交都是真实行为，
 * 而不是 Mockito 里假装出来的调用序列。</p>
 *
 * <p>三课堂侧用真的 {@link LegacyClassroomJdbcGateway} 打在真的表上，
 * 这样「重复同步不产生重复听课记录」验证的是实际执行的 SQL，不是打桩。</p>
 */
@Configuration
@EnableTransactionManagement
public class OrderIntegrationTestConfig {

    @Bean
    public SecurityContext securityContext() {
        return new SecurityContextAdapter();
    }

    @Bean
    @Primary
    public DataSource dataSource() {
        return h2DataSource("han", "sdfz/order/han-schema.sql");
    }

    @Bean
    public DataSource legacyDataSource() {
        return h2DataSource("legacy", "sdfz/order/legacy-schema.sql");
    }

    private static DataSource h2DataSource(String prefix, String schema) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        // 每个上下文一个独立库，避免并行执行的测试类互相看见对方的数据。
        dataSource.setJdbcUrl("jdbc:h2:mem:" + prefix + "-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaximumPoolSize(8);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(schema));
        DatabasePopulatorUtils.execute(populator, dataSource);
        return dataSource;
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    public TenantProperties tenantProperties() {
        return new TenantProperties();
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(TenantProperties tenantProperties,
                                                         SecurityContext securityContext) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(
                new TenantLineInnerInterceptor(new HanTenantLineHandler(tenantProperties, securityContext)));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }

    @Bean
    public MybatisSqlSessionFactoryBean sqlSessionFactory(DataSource dataSource,
                                                          MybatisPlusInterceptor interceptor,
                                                          SecurityContext securityContext) {
        TenantHelper.setSecurityContext(securityContext);

        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        dbConfig.setIdType(IdType.ASSIGN_ID);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(dbConfig);
        globalConfig.setBanner(false);
        globalConfig.setMetaObjectHandler(new HanMetaObjectHandler(securityContext));

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCacheEnabled(false);

        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setGlobalConfig(globalConfig);
        factory.setConfiguration(configuration);
        factory.setPlugins(interceptor);
        return factory;
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplate(
            org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean
    public static MapperScannerConfigurer mapperScannerConfigurer() {
        MapperScannerConfigurer configurer = new MapperScannerConfigurer();
        configurer.setBasePackage("com.han.system.sdfz.order.mapper,com.han.system.sdfz.education.mapper");
        configurer.setSqlSessionTemplateBeanName("sqlSessionTemplate");
        return configurer;
    }

    @Bean
    public LegacyClassroomProperties legacyClassroomProperties() {
        return new LegacyClassroomProperties();
    }

    @Bean
    public JdbcTemplate legacyJdbcTemplate(@Qualifier("legacyDataSource") DataSource legacyDataSource) {
        return new JdbcTemplate(legacyDataSource);
    }

    @Bean
    public JdbcTemplate hanJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * 真实 JDBC 网关外面套一层开关，用来注入故障验证 ORDER-09 的重试路径。
     */
    @Bean
    public FaultInjectingClassroomGateway classroomGateway(
            @Qualifier("legacyJdbcTemplate") JdbcTemplate legacyJdbcTemplate,
            LegacyClassroomProperties properties) {
        return new FaultInjectingClassroomGateway(
                new LegacyClassroomJdbcGateway(legacyJdbcTemplate, properties));
    }

    @Bean
    public CourseGrantLedger courseGrantLedger(
            com.han.system.sdfz.order.mapper.EduCourseOrderGrantMapper grantMapper) {
        return new CourseGrantLedger(grantMapper);
    }

    @Bean
    public CourseGrantService courseGrantService(
            com.han.system.sdfz.order.mapper.EduCourseOrderMapper orderMapper,
            com.han.system.sdfz.order.mapper.EduCourseOrderSubjectMapper orderSubjectMapper,
            com.han.system.sdfz.order.mapper.EduCourseOrderGrantMapper grantMapper,
            com.han.system.sdfz.education.mapper.EduSubjectMapper subjectMapper,
            com.han.system.sdfz.education.mapper.EduSchoolMapper schoolMapper,
            com.han.system.sdfz.education.mapper.EduClassMapper classMapper,
            com.han.system.sdfz.education.mapper.EduRoomMapper roomMapper,
            com.han.system.sdfz.education.mapper.EduDeviceMapper deviceMapper,
            CourseGrantLedger ledger,
            FaultInjectingClassroomGateway gateway) {
        return new CourseGrantService(orderMapper, orderSubjectMapper, grantMapper, subjectMapper,
                schoolMapper, classMapper, roomMapper, deviceMapper, ledger, gateway);
    }

    @Bean
    public CourseOrderService courseOrderService(
            com.han.system.sdfz.order.mapper.EduCourseOrderMapper orderMapper,
            com.han.system.sdfz.order.mapper.EduCourseOrderSubjectMapper orderSubjectMapper,
            com.han.system.sdfz.order.mapper.EduCourseOrderGrantMapper grantMapper,
            com.han.system.sdfz.education.mapper.EduClassMapper classMapper,
            com.han.system.sdfz.education.mapper.EduRoomMapper roomMapper,
            com.han.system.sdfz.education.mapper.EduDeviceMapper deviceMapper,
            com.han.system.sdfz.education.mapper.EduSemesterMapper semesterMapper,
            com.han.system.sdfz.education.mapper.EduSubjectMapper subjectMapper,
            CourseGrantService grantService) {
        return new CourseOrderService(orderMapper, orderSubjectMapper, grantMapper, classMapper, roomMapper,
                deviceMapper, semesterMapper, subjectMapper, grantService);
    }

    @Bean
    public EducationCalendarService educationCalendarService(
            com.han.system.sdfz.education.mapper.EduSemesterMapper semesterMapper,
            com.han.system.sdfz.education.mapper.EduRoomMapper roomMapper,
            com.han.system.sdfz.education.mapper.EduSchoolMapper schoolMapper,
            com.han.system.sdfz.education.mapper.EduDeviceMapper deviceMapper,
            com.han.system.sdfz.education.mapper.EduAcademicYearMapper academicYearMapper) {
        return new EducationCalendarService(semesterMapper, roomMapper, schoolMapper, deviceMapper, academicYearMapper,
                org.mockito.Mockito.mock(com.han.system.sdfz.education.EducationDataScopeService.class));
    }

    @Bean
    public CourseOrderTenantScope courseOrderTenantScope(
            com.han.system.sdfz.order.mapper.EduCourseOrderMapper orderMapper) {
        return new CourseOrderTenantScope(orderMapper);
    }

    @Bean
    public OrderTestFixtures orderTestFixtures(@Qualifier("hanJdbcTemplate") JdbcTemplate hanJdbcTemplate,
                                               @Qualifier("legacyJdbcTemplate") JdbcTemplate legacyJdbcTemplate) {
        return new OrderTestFixtures(hanJdbcTemplate, legacyJdbcTemplate);
    }
}
