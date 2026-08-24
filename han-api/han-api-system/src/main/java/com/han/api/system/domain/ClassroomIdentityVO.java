package com.han.api.system.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Han 本地账号在三个课堂里的教育身份，供签发兼容凭证使用。
 *
 * <p>只包含组装 claims 所需的字段，不携带手机号、密码等敏感项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomIdentityVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 旧侧的全局用户标识，取 edu_person.user_id，为空时回落到人员主键。 */
    private String userId;

    /** 身份主键，取 edu_person.id。 */
    private String identityId;

    /**
     * 数字校园外部身份标识（{@code edu_person.external_identity_id}）。
     *
     * <p>仅数字校园来源（{@code source_system=DIGITAL_CAMPUS}）人员非空，
     * 供 han-auth 用稳定外部身份 ID 精确解析本地 {@link #identityId}，不再按「学校名 + 姓名」匹配。
     */
    private String externalIdentityId;

    private String userName;

    /** 教育人员类型，如 TEACHER / STUDENT。 */
    private String personType;

    /** 校内岗位；学生为空或普通学生默认值。 */
    private String dutyCode;

    /** 当前有效班级 ID；学生凭证用于服务端课程范围收敛。 */
    private List<String> classIds;

    /** 由 person_type 映射出的旧 roleType，本期只有 2（教师）可以换取凭证。 */
    private String roleType;

    private String schoolId;

    private String schoolName;

    /**
     * Han 的 {@code edu_person.status}：0 启用 / 1 停用。
     *
     * <p>这是 Han 内部语义，不是旧三课堂那个兼作软删除标志的 {@code status}，两者不要互相赋值。
     */
    private Integer status;

    /** 当前部署策略下是否允许该身份换取 Classroom Token。 */
    private boolean loginAllowed;

    /** 写入 Token 的角色集合。 */
    private List<String> roles;

    /**
     * PC 管理端是否可用，按「人员/学校/岗位/管理角色」四要素计算：
     * 人员与学校均有效，且岗位为校内管理员（{@code dutyCode=SCHOOL_ADMIN}），
     * 且账号关联了非 teacher/student 的管理端角色。
     *
     * <p>与 {@link #loginAllowed} 是两个维度：{@code loginAllowed} 管能否进课堂，
     * 本字段管能否以该身份进 PC 管理端。普通教师 {@code loginAllowed=true} 但
     * {@code managementAvailable=false} 是常态。
     */
    private boolean managementAvailable;

    /** {@code managementAvailable=false} 时的原因文案；可用时为空串。 */
    private String managementUnavailableReason;
}
