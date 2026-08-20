package com.han.system.sdfz.education.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_class")
public class EduClassPo extends BizEntity {
    private Long schoolId;
    private Long parentId;
    private String ancestors;
    private Integer nodeLevel;
    private Integer sort;
    private String nodeType;
    private Long academicYearId;
    private Integer cohortYear;
    private String branchCode;
    private String gradeCode;
    private String classCode;
    private String className;
    private String classRole;
    private String sourceSystem;
    private String externalId;
    private Integer status;
    private String syncHash;
    private LocalDateTime lastSyncTime;
}
