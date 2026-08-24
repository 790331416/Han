package com.han.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 学校身份摘要（供登录选择与身份切换列表展示）。
 *
 * <p>与 {@code han-api-system} 的 {@code ClassroomIdentityVO} 区分：本 VO 只携带前端
 * 选择身份和展示当前身份所需字段，不携带班级、旧 roleType 等兼容凭证细节。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityVO {

    /** 身份主键（edu_person.id） */
    private Long identityId;

    /** 学校ID */
    private Long schoolId;

    /** 学校名称 */
    private String schoolName;

    /** 教育人员类型 TEACHER / STUDENT */
    private String personType;

    /** 校内岗位编码 TEACHER / SCHOOL_ADMIN */
    private String dutyCode;

    /** 岗位中文名 */
    private String dutyName;

    /** 身份展示名（姓名） */
    private String identityDisplayName;

    /** 是否为当前生效身份 */
    private boolean current;

    /**
     * PC 管理端是否可用：人员与学校有效、personType=TEACHER、dutyCode=SCHOOL_ADMIN
     * 且账号关联了非 teacher/student 管理角色。数据来自 {@code ClassroomIdentityVO}，
     * 缺失或不可判定时保守为 false。
     */
    private boolean managementAvailable;

    /** {@code managementAvailable=false} 时的原因文案；可用时为空串。 */
    private String managementUnavailableReason;
}
