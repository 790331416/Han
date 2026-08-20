package com.han.system.sdfz.education.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 学年升级批次逐人明细；成功记录不可被第二次确认重复写入。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_grade_promotion_item")
public class EduGradePromotionItemPo extends TenantEntity {
    private Long batchId;
    private Long personId;
    private Long sourceClassId;
    private Long targetClassId;
    private String action;
    private String resultStatus;
    private String errorMessage;
}
