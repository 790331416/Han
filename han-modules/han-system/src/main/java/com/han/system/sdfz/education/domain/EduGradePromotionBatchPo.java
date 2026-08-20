package com.han.system.sdfz.education.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 学年升级批次；当前仅用于保护被引用的学年不被误删。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_grade_promotion_batch")
public class EduGradePromotionBatchPo extends BizEntity {
    private Long schoolId;
    private Long sourceAcademicYearId;
    private Long targetAcademicYearId;
    private String status;
    private String idempotencyKey;
    private Integer version;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
}
