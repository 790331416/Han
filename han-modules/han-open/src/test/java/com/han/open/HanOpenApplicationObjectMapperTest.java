package com.han.open;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.common.core.util.HanJsonUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class HanOpenApplicationObjectMapperTest {

    @Test
    void exposesSharedObjectMapperOnlyWhenNoApplicationMapperExists() throws Exception {
        Method method = HanOpenApplication.class.getDeclaredMethod("objectMapper");

        assertThat(method.getAnnotation(Bean.class)).isNotNull();
        ConditionalOnMissingBean condition = method.getAnnotation(ConditionalOnMissingBean.class);
        assertThat(condition).isNotNull();
        assertThat(condition.value()).containsExactly(ObjectMapper.class);
        assertThat(method.invoke(new HanOpenApplication()))
                .isSameAs(HanJsonUtil.getObjectMapper());
    }
}
