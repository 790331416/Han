package com.han.system.sdfz.education.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_semester")
public class EduSemesterPo extends BizEntity {
    private String semesterCode;
    private String semesterName;
    private LocalDate beginDate;
    private LocalDate endDate;
    private Integer currentFlag;
    private Integer status;
}
