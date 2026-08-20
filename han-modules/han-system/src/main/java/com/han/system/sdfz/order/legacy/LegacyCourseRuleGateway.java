package com.han.system.sdfz.order.legacy;

import java.util.List;

/** 复用三课堂数据库连接的课表规则读写端口。 */
public interface LegacyCourseRuleGateway {

    List<LegacyCourseRule> listCourseRules(String templateId);

    LegacyCourseRule findCourseRule(String ruleId);

    String insertCourseRule(String templateId, String templateName, String startTime, String endTime,
                             String classSection, String operatorId, String operatorName);

    void updateCourseRule(String ruleId, String templateId, String templateName, String startTime,
                          String endTime, String classSection, String operatorId, String operatorName);

    void updateCourseRuleStatus(String ruleId, String status, String operatorId, String operatorName);

    long countCourseReferences(String ruleId);

    void deleteCourseRule(String ruleId);
}
