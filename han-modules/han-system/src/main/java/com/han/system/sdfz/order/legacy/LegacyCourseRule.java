package com.han.system.sdfz.order.legacy;

/** 三课堂节次规则的最小读模型。 */
public record LegacyCourseRule(
        String ruleId,
        String templateId,
        String templateName,
        String startTime,
        String endTime,
        String classSection,
        String status,
        String createId,
        String createName,
        String createTime,
        String updateId,
        String updateName,
        String updateTime) {
}
