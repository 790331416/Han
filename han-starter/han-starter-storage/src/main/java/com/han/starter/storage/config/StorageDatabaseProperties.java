package com.han.starter.storage.config;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Database connection properties for runtime storage configuration lookup.
 */
public final class StorageDatabaseProperties {

    private final String url;
    private final String username;
    private final String password;

    private StorageDatabaseProperties(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public static StorageDatabaseProperties fromEnvironment(Environment environment) {
        String url = firstNonBlank(
                environment.getProperty("spring.datasource.url"),
                buildUrlFromDbEnv(environment)
        );
        String username = firstNonBlank(
                environment.getProperty("spring.datasource.username"),
                environment.getProperty("DB_USER")
        );
        String password = firstNonBlank(
                environment.getProperty("spring.datasource.password"),
                environment.getProperty("DB_PASSWORD")
        );
        return new StorageDatabaseProperties(url, username, password);
    }

    public boolean isConfigured() {
        return StringUtils.hasText(url) && StringUtils.hasText(username);
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    private static String buildUrlFromDbEnv(Environment environment) {
        String host = environment.getProperty("DB_HOST");
        String port = environment.getProperty("DB_PORT", "5432");
        String database = environment.getProperty("DB_NAME");
        if (!StringUtils.hasText(host) || !StringUtils.hasText(database)) {
            return null;
        }
        return "jdbc:postgresql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
