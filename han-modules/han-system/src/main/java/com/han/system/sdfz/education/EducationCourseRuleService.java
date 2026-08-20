package com.han.system.sdfz.education;

import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.system.sdfz.education.domain.EducationCourseRuleForms;
import com.han.system.sdfz.order.legacy.LegacyCourseRule;
import com.han.system.sdfz.order.legacy.LegacyCourseRuleGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 管理端维护三课堂统一节次规则；校端预约和课表展示共用这张表。 */
@Service
@RequiredArgsConstructor
public class EducationCourseRuleService {

    public static final String DEFAULT_TEMPLATE_ID = "1";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final LegacyCourseRuleGateway gateway;

    public List<LegacyCourseRule> list() {
        return gateway.listCourseRules(DEFAULT_TEMPLATE_ID).stream()
                .sorted(Comparator.comparingInt(item -> parseSection(item.classSection())))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public String add(EducationCourseRuleForms.Rule form) {
        RuleValue value = validate(form, null);
        return gateway.insertCourseRule(DEFAULT_TEMPLATE_ID, value.templateName(), value.startTime(), value.endTime(),
                value.classSection(), operatorId(), operatorName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void edit(EducationCourseRuleForms.Rule form) {
        RuleValue value = validate(form, form.id());
        requireRule(form.id());
        gateway.updateCourseRule(form.id(), DEFAULT_TEMPLATE_ID, value.templateName(), value.startTime(), value.endTime(),
                value.classSection(), operatorId(), operatorName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(EducationCourseRuleForms.Status form) {
        LegacyCourseRule existing = requireRule(form.id());
        if ("0".equals(form.status())) {
            validateNoConflict(existing.ruleId(), existing.templateId(), existing.startTime(), existing.endTime(),
                    existing.classSection());
        }
        gateway.updateCourseRuleStatus(existing.ruleId(), form.status(), operatorId(), operatorName());
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteRules(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的节次");
        }
        int deleted = 0;
        for (String id : ids.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).distinct().toList()) {
            LegacyCourseRule rule = requireRule(id);
            long references = gateway.countCourseReferences(id);
            if (references > 0) {
                throw new BusinessException("第" + rule.classSection() + "节仍被" + references + "门课程引用，只能停用不能删除");
            }
            gateway.deleteCourseRule(id);
            deleted++;
        }
        return deleted;
    }

    private RuleValue validate(EducationCourseRuleForms.Rule form, String currentId) {
        String templateName = form.templateName().trim();
        String start = normalizeTime(form.startTime());
        String end = normalizeTime(form.endTime());
        int section = parseSection(form.classSection());
        if (!LocalTime.parse(start, TIME).isBefore(LocalTime.parse(end, TIME))) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
        validateNoConflict(currentId, DEFAULT_TEMPLATE_ID, start, end, String.valueOf(section));
        return new RuleValue(templateName, start, end, String.valueOf(section));
    }

    private void validateNoConflict(String currentId, String templateId, String start, String end,
                                    String classSection) {
        LocalTime startTime = LocalTime.parse(normalizeTime(start), TIME);
        LocalTime endTime = LocalTime.parse(normalizeTime(end), TIME);
        for (LegacyCourseRule item : gateway.listCourseRules(templateId)) {
            if (Objects.equals(item.ruleId(), currentId)) {
                continue;
            }
            if (!"0".equals(item.status())) {
                continue;
            }
            if (Objects.equals(item.classSection(), classSection)) {
                throw new BusinessException("第" + classSection + "节已存在，请调整节次编号");
            }
            LocalTime otherStart = LocalTime.parse(normalizeTime(item.startTime()), TIME);
            LocalTime otherEnd = LocalTime.parse(normalizeTime(item.endTime()), TIME);
            if (startTime.isBefore(otherEnd) && otherStart.isBefore(endTime)) {
                throw new BusinessException("时间段 " + start + " - " + end + " 与第" + item.classSection() + "节重叠");
            }
        }
    }

    private LegacyCourseRule requireRule(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException("节次编号不能为空");
        }
        LegacyCourseRule rule = gateway.findCourseRule(id);
        if (rule == null || !Objects.equals(DEFAULT_TEMPLATE_ID, rule.templateId())) {
            throw new BusinessException("节次不存在");
        }
        return rule;
    }

    private static int parseSection(String value) {
        try {
            int section = Integer.parseInt(value);
            if (section > 0) return section;
        } catch (NumberFormatException ignored) {
            // 统一转换为业务提示
        }
        throw new BusinessException("节次必须是正整数");
    }

    private static String normalizeTime(String value) {
        String raw = value.trim();
        try {
            if (raw.length() == 5) raw += ":00";
            return LocalTime.parse(raw, TIME).format(TIME);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("时间格式应为 HH:mm，例如 08:00");
        }
    }

    private static String operatorId() {
        Long id = SecurityContextHolder.getUserId();
        return id == null ? "han-admin" : String.valueOf(id);
    }

    private static String operatorName() {
        String username = SecurityContextHolder.getUsername();
        return username == null || username.isBlank() ? "Han管理员" : username;
    }

    private record RuleValue(String templateName, String startTime, String endTime, String classSection) {
    }
}
