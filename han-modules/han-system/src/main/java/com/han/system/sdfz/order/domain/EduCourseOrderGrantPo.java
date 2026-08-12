package com.han.system.sdfz.order.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 授权物化台账：某张订购单把某节课的听课权限物化成了三课堂的哪一条 tb_course_attend。
 *
 * <p>唯一键 {@code (tenant_id, order_id, course_id)} 是「重复同步不产生重复听课记录」的结构性保证。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_course_order_grant")
public class EduCourseOrderGrantPo extends BizEntity {

    private Long orderId;

    /** 三课堂 tb_course_info.course_id，19 位雪花的十进制串。 */
    private String courseId;

    /** 课程名称快照，台账页面直接展示，不回查旧库。 */
    private String courseName;

    /** 上课时间快照。取消订购时按它区分「未开始的课撤销、已结束的课保留回放」。 */
    private LocalDateTime courseBeginTime;

    /** 冗余自订购单：引用计数撤销按 (course_id, listen_class_id) 聚合。 */
    private Long listenClassId;

    private Long subjectId;

    /** 物化成功后回填的 tb_course_attend.attend_id。 */
    private String attendId;

    /** {@link GrantStatus} 的名字。 */
    private String grantStatus;

    /**
     * 订购单冻结时置 1。台账仍是 MATERIALIZED（恢复时要靠它知道原来授权过什么），
     * 但听课记录已经失效，因此不计入引用计数。
     */
    private Integer suspendedFlag;

    private Integer attemptCount;
    private String lastError;
    private LocalDateTime lastAttemptTime;
    private LocalDateTime materializedTime;
    private LocalDateTime revokedTime;

    public GrantStatus status() {
        return GrantStatus.parse(grantStatus);
    }

    public boolean isSuspended() {
        return suspendedFlag != null && suspendedFlag == 1;
    }
}
