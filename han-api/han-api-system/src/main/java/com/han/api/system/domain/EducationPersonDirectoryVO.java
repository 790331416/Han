package com.han.api.system.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 面向已授权第三方应用的教师/学生目录行，不携带账号或联系方式。 */
public record EducationPersonDirectoryVO(
        Long personId,
        String personNo,
        String personName,
        String personType,
        String dutyCode,
        Long schoolId,
        String schoolName,
        List<EducationClassSummaryVO> classes,
        Integer status,
        LocalDateTime updatedAt
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
