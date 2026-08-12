package com.han.system.sdfz.order.legacy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;

/**
 * 物化通道装配。
 *
 * <p>JDBC 通道用独立的 {@link DataSource}，不复用 Han 主库连接池：两边是不同的库、不同的账号，
 * 而且要保证「Han 侧事务提交与三课堂写入互不牵连」——物化本来就不能和台账放进同一个事务
 * （跨库无法原子），台账的最终一致靠对账任务兜底。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LegacyClassroomProperties.class)
public class LegacyClassroomConfig {

    @Bean
    public LegacyClassroomGateway legacyClassroomGateway(LegacyClassroomProperties properties) {
        return switch (properties.getChannel()) {
            case JDBC -> jdbcGateway(properties);
            case HTTP -> httpGateway(properties);
            case DISABLED -> {
                log.info("三课堂物化通道未启用，订购单只在 Han 侧闭环");
                yield new DisabledLegacyClassroomGateway();
            }
        };
    }

    private LegacyClassroomGateway jdbcGateway(LegacyClassroomProperties properties) {
        LegacyClassroomProperties.Jdbc jdbc = properties.getJdbc();
        if (jdbc.getUrl() == null || jdbc.getUrl().isBlank()) {
            log.warn("sdfz.order.legacy.channel=jdbc 但未配置 url，退回未启用状态");
            return new DisabledLegacyClassroomGateway();
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("sdfz-legacy-classroom");
        config.setJdbcUrl(jdbc.getUrl());
        config.setUsername(jdbc.getUsername());
        config.setPassword(jdbc.getPassword());
        config.setMaximumPoolSize(jdbc.getMaximumPoolSize());
        config.setConnectionTimeout(jdbc.getConnectionTimeout().toMillis());
        config.setReadOnly(false);
        config.setAutoCommit(true);
        log.info("三课堂物化通道: JDBC 直连，连接池 {}", config.getPoolName());
        return new LegacyClassroomJdbcGateway(new JdbcTemplate(new HikariDataSource(config)), properties);
    }

    private LegacyClassroomGateway httpGateway(LegacyClassroomProperties properties) {
        LegacyClassroomProperties.Http http = properties.getHttp();
        if (http.getBaseUrl() == null || http.getBaseUrl().isBlank()) {
            log.warn("sdfz.order.legacy.channel=http 但未配置 base-url，退回未启用状态");
            return new DisabledLegacyClassroomGateway();
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(http.getConnectTimeout());
        factory.setReadTimeout(http.getReadTimeout());
        log.info("三课堂物化通道: 旧 api 内部接口");
        return new LegacyClassroomHttpGateway(
                RestClient.builder().requestFactory(factory).build(),
                http.getBaseUrl(),
                http.getToken());
    }
}
