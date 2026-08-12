package com.han.system.sdfz.education.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_person_subject")
public class EduPersonSubjectPo extends TenantEntity {
    private Long personId;
    private Long subjectId;
    private Long classId;
}
