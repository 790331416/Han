package com.han.system.sdfz.order.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 订购单主表。
 *
 * <p>表上还有一个生成列 {@code active_flag}，只为承载唯一约束
 * {@code uq_edu_course_order_active}，由数据库维护。这里刻意不映射它——
 * 映射了 MyBatis-Plus 会在 insert/update 里带上该列，MySQL 会直接拒绝写生成列。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_course_order")
public class EduCourseOrderPo extends BizEntity {

    private String orderNo;
    private Long listenSchoolId;
    private Long listenClassId;

    /** 听讲教室，物化为 tb_course_attend.place_id；未绑定时听课记录的场所留空。 */
    private Long listenRoomId;

    /** 听讲端设备，物化为 tb_course_attend.member_id，旧系统「加入课堂」按它反查听课行。 */
    private Long listenDeviceId;

    private Long lectureSchoolId;
    private Long lectureClassId;
    private Long semesterId;

    /** {@link GrantScope} 的名字。 */
    private String grantScope;

    /** {@link OrderStatus} 的名字。 */
    private String status;

    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private String freezeReason;
    private String cancelReason;
    private String sourceSystem;
    private String externalId;

    public GrantScope scope() {
        return GrantScope.parse(grantScope);
    }

    public OrderStatus orderStatus() {
        return OrderStatus.parse(status);
    }
}
