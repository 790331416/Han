package com.han.api.system.domain;

import java.io.Serial;
import java.io.Serializable;

/** 对外目录中的班级摘要，不包含学生名单和内部审计字段。 */
public record EducationClassSummaryVO(Long classId, String className) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
