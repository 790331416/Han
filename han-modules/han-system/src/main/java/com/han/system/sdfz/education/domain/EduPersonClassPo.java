package com.han.system.sdfz.education.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_person_class")
public class EduPersonClassPo extends TenantEntity {
    private Long personId;
    private Long classId;
    private Long academicYearId;
    private String membershipRole;
    private String membershipStatus;
    private java.time.LocalDateTime effectiveStartAt;
    private java.time.LocalDateTime effectiveEndAt;
    private Long promotionBatchId;
    private String sourceSystem;
}
