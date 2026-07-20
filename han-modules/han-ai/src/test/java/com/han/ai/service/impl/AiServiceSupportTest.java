package com.han.ai.service.impl;

import com.han.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 开场推荐问题校验单测（G1-10）：JSON 字符串数组、条数上限、单条非空与长度限制。
 */
class AiServiceSupportTest {

    private static class TestSupport extends AiServiceSupport {
    }

    private final TestSupport support = new TestSupport();

    @Test
    void allowsBlankAndValidArray() {
        assertThatCode(() -> {
            support.validateSuggestedQuestions(null);
            support.validateSuggestedQuestions("");
            support.validateSuggestedQuestions("[]");
            support.validateSuggestedQuestions("[\"如何创建智能体？\",\"支持哪些模型？\"]");
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonJsonAndNonArray() {
        assertThatThrownBy(() -> support.validateSuggestedQuestions("not-json"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("合法JSON");
        assertThatThrownBy(() -> support.validateSuggestedQuestions("{\"q\":\"x\"}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字符串数组");
    }

    @Test
    void rejectsMoreThanFiveQuestions() {
        assertThatThrownBy(() -> support.validateSuggestedQuestions(
                "[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多 5 条");
    }

    @Test
    void rejectsBlankOrNonTextItems() {
        assertThatThrownBy(() -> support.validateSuggestedQuestions("[\"有效问题\",\"  \"]"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非空文本");
        assertThatThrownBy(() -> support.validateSuggestedQuestions("[123]"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非空文本");
    }

    @Test
    void rejectsOverlongQuestion() {
        assertThatThrownBy(() -> support.validateSuggestedQuestions("[\"" + "长".repeat(201) + "\"]"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过 200 字");
    }
}
