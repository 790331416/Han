package com.han.gen.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseUrlPlaceholderTest {

    private static final String DATABASE_URL =
            "${DB_URL:jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:han}}";

    @Test
    void keepsPostgresDefaultAndAllowsMysqlOverride() {
        MockEnvironment defaults = new MockEnvironment();
        assertEquals("jdbc:postgresql://localhost:5432/han", defaults.resolveRequiredPlaceholders(DATABASE_URL));

        MockEnvironment mysql = new MockEnvironment()
                .withProperty("DB_URL", "jdbc:mysql://127.0.0.1:3306/han");
        assertEquals("jdbc:mysql://127.0.0.1:3306/han", mysql.resolveRequiredPlaceholders(DATABASE_URL));
    }
}
