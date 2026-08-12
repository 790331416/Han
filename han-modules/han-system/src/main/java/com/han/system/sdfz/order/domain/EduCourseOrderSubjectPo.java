package com.han.system.sdfz.order.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订购科目明细。仅 {@link GrantScope#BY_SUBJECT} 的单子有行。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_course_order_subject")
public class EduCourseOrderSubjectPo extends TenantEntity {

    private Long orderId;
    private Long subjectId;
}
