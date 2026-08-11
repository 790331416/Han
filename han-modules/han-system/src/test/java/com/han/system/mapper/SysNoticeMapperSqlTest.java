package com.han.system.mapper;

import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SysNoticeMapperSqlTest {

    @Test
    void latestNoticeQueryAvoidsMysqlReservedReadAlias() throws NoSuchMethodException {
        Method method = SysNoticeMapper.class.getMethod("selectLatestForUser", Long.class, int.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());
        Results results = method.getAnnotation(Results.class);

        assertThat(sql).contains("AS read_flag").doesNotContain("AS read,");
        assertThat(Arrays.stream(results.value()))
                .extracting(Result::column, Result::property)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("read_flag", "read"),
                        org.assertj.core.groups.Tuple.tuple("read_time", "readTime"));
    }
}
