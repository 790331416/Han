package com.han.system.sdfz.education;

import com.han.common.core.exception.BusinessException;
import com.han.system.sdfz.education.domain.EducationCourseRuleForms;
import com.han.system.sdfz.order.legacy.LegacyCourseRule;
import com.han.system.sdfz.order.legacy.LegacyCourseRuleGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationCourseRuleServiceTest {

    @Mock
    private LegacyCourseRuleGateway gateway;

    @Test
    void rejectsOverlappingEnabledRule() {
        when(gateway.listCourseRules("1")).thenReturn(List.of(rule("101", "1", "08:00:00", "08:45:00", "1", "0")));
        EducationCourseRuleService service = new EducationCourseRuleService(gateway);

        assertThatThrownBy(() -> service.add(new EducationCourseRuleForms.Rule(
                null, "默认作息", "08:30", "09:10", "2")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重叠");
        verify(gateway, never()).insertCourseRule(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void rejectsInvalidTimeRange() {
        EducationCourseRuleService service = new EducationCourseRuleService(gateway);

        assertThatThrownBy(() -> service.add(new EducationCourseRuleForms.Rule(
                null, "默认作息", "10:00", "09:00", "1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结束时间");
    }

    @Test
    void rejectsDeletingReferencedRule() {
        LegacyCourseRule existing = rule("101", "1", "08:00:00", "08:45:00", "1", "0");
        when(gateway.findCourseRule("101")).thenReturn(existing);
        when(gateway.countCourseReferences("101")).thenReturn(1L);
        EducationCourseRuleService service = new EducationCourseRuleService(gateway);

        assertThatThrownBy(() -> service.deleteRules(List.of("101")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能停用不能删除");
        verify(gateway, never()).deleteCourseRule("101");
    }

    @Test
    void rejectsMutatingRuleFromAnotherTemplate() {
        when(gateway.findCourseRule("202")).thenReturn(
                rule("202", "2", "08:00:00", "08:45:00", "1", "0"));
        EducationCourseRuleService service = new EducationCourseRuleService(gateway);

        assertThatThrownBy(() -> service.changeStatus(
                new EducationCourseRuleForms.Status("202", "1")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("节次不存在");
        verify(gateway, never()).updateCourseRuleStatus(anyString(), anyString(), anyString(), anyString());
    }

    private static LegacyCourseRule rule(String id, String templateId, String start, String end,
                                         String section, String status) {
        return new LegacyCourseRule(id, templateId, "默认作息", start, end, section, status,
                "seed", "seed", "2026-08-19 00:00:00", null, null, null);
    }
}
