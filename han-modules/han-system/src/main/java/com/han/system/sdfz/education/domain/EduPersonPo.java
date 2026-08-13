package com.han.system.sdfz.education.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_person")
public class EduPersonPo extends BizEntity {
    private Long userId;
    private Long schoolId;
    private String personNo;
    private String personName;
    private String personType;

    /**
     * 校内岗位（{@link EduDuty} 的名字，为空按普通教师处理）。
     *
     * <p>与 {@code personType} 是两个维度：{@code personType} 是身份类型（教师/学生），
     * 本字段是职务。旧三课堂的控制台菜单按岗位授权，只有显式授予校级管理岗的人
     * 才拿得到课程预约这类校级页面，不能靠身份类型推导。</p>
     */
    private String dutyCode;

    private String phone;
    private String sourceSystem;
    private String externalUserId;
    private String externalIdentityId;
    private Integer status;

    /**
     * 离校标记（0=在校 1=离校）。
     *
     * <p>与账号停用是两件事：{@code sys_user.status} 管登录能力，本字段管教育身份是否在校，
     * 两者可以独立变化（验收方案 §4.13 里"仅人员停用"与"Han 账号禁用"是两行）。
     * 离校后不参与新课程，历史课程与审计按 ID 关联保留。</p>
     */
    private Integer leaveFlag;

    /** 离校时间，由 leaveFlag 从 0 变 1 时写入，恢复在校时清空。 */
    private LocalDateTime leaveTime;

    private String syncHash;
    private LocalDateTime lastSyncTime;
}
