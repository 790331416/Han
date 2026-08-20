package com.han.system.sdfz.order.legacy;

import java.util.List;

/** 未配置三课堂 JDBC 通道时的明确失败实现。 */
public class DisabledLegacyCourseRuleGateway implements LegacyCourseRuleGateway {

    private static final String MESSAGE = "三课堂数据库通道未启用，请先配置 sdfz.order.legacy.channel=jdbc";

    @Override
    public List<LegacyCourseRule> listCourseRules(String templateId) {
        throw LegacyClassroomException.permanent(MESSAGE);
    }

    @Override
    public LegacyCourseRule findCourseRule(String ruleId) {
        throw LegacyClassroomException.permanent(MESSAGE);
    }

    @Override
    public String insertCourseRule(String templateId, String templateName, String startTime, String endTime,
                                   String classSection, String operatorId, String operatorName) {
        throw LegacyClassroomException.permanent(MESSAGE);
    }

    @Override
    public void updateCourseRule(String ruleId, String templateId, String templateName, String startTime,
                                 String endTime, String classSection, String operatorId, String operatorName) {
        throw LegacyClassroomException.permanent(MESSAGE);
    }

    @Override
    public void updateCourseRuleStatus(String ruleId, String status, String operatorId, String operatorName) {
        throw LegacyClassroomException.permanent(MESSAGE);
    }

    @Override
    public long countCourseReferences(String ruleId) {
        throw LegacyClassroomException.permanent(MESSAGE);
    }

    @Override
    public void deleteCourseRule(String ruleId) {
        throw LegacyClassroomException.permanent(MESSAGE);
    }
}
