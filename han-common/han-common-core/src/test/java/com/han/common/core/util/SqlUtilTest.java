package com.han.common.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SqlUtil} 单元测试，重点覆盖工单 S-72 的注入面。
 */
class SqlUtilTest {

    /** 经典逃逸载荷：未转义时会闭合字面量并注入恒真条件 */
    private static final String ESCAPE_PAYLOAD = "x' OR '1'='1";

    @Test
    @DisplayName("buildLikeClause 必须转义单引号，载荷不能逃逸出字符串字面量")
    void buildLikeClauseEscapesSingleQuote() {
        String clause = SqlUtil.buildLikeClause("user_name", ESCAPE_PAYLOAD);

        assertEquals("user_name LIKE '%x'' OR ''1''=''1%'", clause);
        assertTrue(hasBalancedQuotes(clause), "转义后单引号必须成对出现: " + clause);
    }

    @Test
    @DisplayName("buildLikeClause 仍然转义 LIKE 通配符")
    void buildLikeClauseEscapesWildcards() {
        assertEquals("code LIKE '%a\\%b\\_c%'", SqlUtil.buildLikeClause("code", "a%b_c"));
    }

    @Test
    @DisplayName("buildBetweenClause 的字符串端点必须加引号并转义")
    void buildBetweenClauseQuotesStringBounds() {
        String clause = SqlUtil.buildBetweenClause("create_time", "2026-01-01' OR '1'='1", "2026-12-31");

        assertEquals("create_time BETWEEN '2026-01-01'' OR ''1''=''1' AND '2026-12-31'", clause);
        assertTrue(hasBalancedQuotes(clause));
    }

    @Test
    @DisplayName("buildBetweenClause 的数值端点不加引号")
    void buildBetweenClauseKeepsNumericBoundsRaw() {
        assertEquals("age BETWEEN 18 AND 65", SqlUtil.buildBetweenClause("age", 18, 65));
        assertEquals("amount >= 12.50", SqlUtil.buildBetweenClause("amount", new BigDecimal("12.50"), null));
    }

    @Test
    @DisplayName("buildInClause 对非字符串值同样加引号转义")
    void buildInClauseQuotesNonNumericValues() {
        assertEquals("status IN ('a''b', 1, NULL)", SqlUtil.buildInClause("status", "a'b", 1, null));
    }

    @Test
    @DisplayName("三个拼接方法都必须拒绝非法列名")
    void rejectsIllegalColumnName() {
        String malicious = "id; DROP TABLE sys_user --";

        assertThrows(IllegalArgumentException.class, () -> SqlUtil.buildLikeClause(malicious, "x"));
        assertThrows(IllegalArgumentException.class, () -> SqlUtil.buildInClause(malicious, 1));
        assertThrows(IllegalArgumentException.class, () -> SqlUtil.buildBetweenClause(malicious, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> SqlUtil.checkColumnName(null));
    }

    @Test
    @DisplayName("合法列名允许一级表别名前缀")
    void acceptsQualifiedColumnName() {
        assertEquals("u.user_name", SqlUtil.checkColumnName("u.user_name"));
    }

    @Test
    @DisplayName("参数化片段不把值拼进 SQL")
    void fragmentsKeepValuesOutOfSql() {
        SqlUtil.SqlFragment like = SqlUtil.likeFragment("user_name", ESCAPE_PAYLOAD);
        assertEquals("user_name LIKE ?", like.sql());
        assertEquals("%" + ESCAPE_PAYLOAD + "%", like.params().get(0));

        SqlUtil.SqlFragment in = SqlUtil.inFragment("id", 1, 2, 3);
        assertEquals("id IN (?, ?, ?)", in.sql());
        assertEquals(3, in.params().size());

        SqlUtil.SqlFragment between = SqlUtil.betweenFragment("age", 18, 65);
        assertEquals("age BETWEEN ? AND ?", between.sql());
        assertEquals(2, between.params().size());
    }

    @Test
    @DisplayName("空条件退化为恒真/恒假片段")
    void fragmentsDegradeOnEmptyInput() {
        assertEquals("1 = 1", SqlUtil.likeFragment("name", "  ").sql());
        assertEquals("1 = 1", SqlUtil.betweenFragment("age", null, null).sql());
        assertEquals("1 = 0", SqlUtil.inFragment("id").sql());
    }

    @Test
    @DisplayName("checkOrderBy 放行合法排序、拦截注入，且不再误伤 order_num 这类列名")
    void checkOrderByUsesWhitelist() {
        assertEquals("create_time DESC", SqlUtil.checkOrderBy("create_time DESC", "id"));
        assertEquals("u.id asc, create_time desc", SqlUtil.checkOrderBy("u.id asc, create_time desc", "id"));
        // 旧的黑名单实现里 \bor 会命中 order_num，把合法列名误判为注入
        assertEquals("order_num", SqlUtil.checkOrderBy("order_num", "id"));

        assertEquals("id", SqlUtil.checkOrderBy("id; DROP TABLE sys_user", "id"));
        assertEquals("id", SqlUtil.checkOrderBy("(SELECT 1)", "id"));
        assertEquals("id", SqlUtil.checkOrderBy(null, "id"));
    }

    /**
     * 判断 SQL 里的单引号是否成对，用来确认转义后没有逃逸出字符串字面量。
     */
    private static boolean hasBalancedQuotes(String sql) {
        return sql.chars().filter(c -> c == '\'').count() % 2 == 0;
    }
}
