package com.han.system.sdfz.education.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_subject")
public class EduSubjectPo extends BizEntity {
    private String subjectCode;
    private String subjectName;
    private String sourceSystem;
    private String externalId;
    private Integer sort;
    private Integer status;
}
