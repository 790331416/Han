package com.han.system.sdfz.compat;

import com.han.api.system.domain.ClassroomIdentityVO;
import com.han.common.core.exception.BusinessException;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 把 Han 用户解析成三课堂教育身份，供 han-auth 从本地登录态换发兼容凭证。
 *
 * <p>只读 {@code edu_person} 与 {@code edu_school}，不查 {@code sys_user_social}：
 * 本地账号不存在任何外部身份绑定，要求绑定行存在会让本地教师直接登不进去。
 *
 * <p>教师和学生都可解析为教育身份；学生令牌的实际业务入口由管理端网关与校端服务端共同收敛，
 * 不能仅凭签发成功获得教师写操作权限。
 *
 * <p>身份有效性同时要求学校有效：人员正常、未离校，且所属学校存在、状态正常、未删除、
 * 与人员同租户。学校无效时该身份不进入列表，选择 / 刷新 / 签发课堂 Token 一律不可用。
 */
@Service
@RequiredArgsConstructor
public class LegacyClassroomIdentityService {

    private static final String MANAGEMENT_UNAVAILABLE_NOT_ADMIN = "当前岗位未开通管理端";
    private static final String MANAGEMENT_UNAVAILABLE_NO_ROLE = "账号未配置管理端角色";

    private final LegacyCompatProperties properties;
    private final LegacyDirectoryService directoryService;

    /** 返回当前账号全部有效教育身份，供 H5/App 在签发课堂凭证前选择身份。 */
    public List<ClassroomIdentityVO> list(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<ClassroomIdentityVO> result = new ArrayList<>();
        for (EduPersonPo person : directoryService.personsByUserId(userId)) {
            if (!isPersonActive(person)) {
                continue;
            }
            EduSchoolPo school = directoryService.schoolById(person.getSchoolId());
            if (!isSchoolActive(person, school)) {
                continue;
            }
            result.add(toIdentity(person, school));
        }
        return result;
    }

    public ClassroomIdentityVO resolve(Long userId) {
        return list(userId).stream()
                .filter(ClassroomIdentityVO::isLoginAllowed)
                .findFirst()
                .orElse(null);
    }

    /**
     * 指定身份必须属于当前账号、启用且在当前策略允许范围内。
     *
     * <p>未指定身份时：多身份账号报业务错误、拒绝默认取第一条；单身份账号保持自动选择兼容。
     */
    public ClassroomIdentityVO resolve(Long userId, String identityId) {
        if (identityId == null || identityId.isBlank()) {
            List<ClassroomIdentityVO> loginAllowed = list(userId).stream()
                    .filter(ClassroomIdentityVO::isLoginAllowed)
                    .toList();
            if (loginAllowed.size() > 1) {
                throw new BusinessException("当前账号存在多个教育身份，请先选择身份");
            }
            return loginAllowed.stream().findFirst().orElse(null);
        }
        return list(userId).stream()
                .filter(item -> identityId.trim().equals(item.getIdentityId()))
                .filter(ClassroomIdentityVO::isLoginAllowed)
                .findFirst()
                .orElse(null);
    }

    private boolean isPersonActive(EduPersonPo person) {
        return person.getStatus() != null && person.getStatus() == 0
                && (person.getLeaveFlag() == null || person.getLeaveFlag() == 0)
                && (person.getDelFlag() == null || person.getDelFlag() == 0);
    }

    /**
     * 学校必须存在、状态正常（未显式停用）、未删除，且与人员同租户。
     *
     * <p>状态未设置（{@code null}）按正常处理，兼容存量数据与无状态字段的旧记录；
     * 显式停用（{@code status=1}）或删除（{@code delFlag=1}）才剔除。
     */
    private boolean isSchoolActive(EduPersonPo person, EduSchoolPo school) {
        if (school == null) {
            return false;
        }
        boolean statusNormal = school.getStatus() == null || school.getStatus() == 0;
        boolean notDeleted = school.getDelFlag() == null || school.getDelFlag() == 0;
        return statusNormal && notDeleted && Objects.equals(person.getTenantId(), school.getTenantId());
    }

    private ClassroomIdentityVO toIdentity(EduPersonPo person, EduSchoolPo school) {
        String roleType = properties.roleTypeOf(person.getPersonType());
        boolean schoolAdmin = isSchoolAdmin(person.getDutyCode());
        boolean hasManagementRole = schoolAdmin
                && directoryService.hasManagementRole(person.getUserId());
        boolean managementAvailable = schoolAdmin && hasManagementRole;
        String unavailableReason = managementAvailable ? ""
                : (!schoolAdmin ? MANAGEMENT_UNAVAILABLE_NOT_ADMIN : MANAGEMENT_UNAVAILABLE_NO_ROLE);
        return ClassroomIdentityVO.builder()
                .userId(directoryService.externalUserId(person))
                .identityId(String.valueOf(person.getId()))
                .userName(person.getPersonName())
                .personType(person.getPersonType())
                .dutyCode(person.getDutyCode())
                .classIds(directoryService.classesOf(person.getId()).stream()
                        .map(item -> String.valueOf(item.getId()))
                        .toList())
                .roleType(roleType)
                .schoolId(person.getSchoolId() == null ? "" : String.valueOf(person.getSchoolId()))
                .schoolName(school != null && school.getSchoolName() != null ? school.getSchoolName() : "")
                .status(person.getStatus() != null ? person.getStatus() : 0)
                .loginAllowed(properties.canIssueToken(person.getPersonType()))
                .roles(List.of(roleType, person.getPersonType()))
                .managementAvailable(managementAvailable)
                .managementUnavailableReason(unavailableReason)
                .build();
    }

    /** 与 han-auth 同口径：dutyCode 为 SCHOOL_ADMIN 才视为校内管理员。 */
    private static boolean isSchoolAdmin(String dutyCode) {
        return dutyCode != null && "SCHOOL_ADMIN".equalsIgnoreCase(dutyCode.trim());
    }
}
