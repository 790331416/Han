package com.han.system.sdfz.compat;

import com.han.api.system.domain.ClassroomIdentityVO;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 把 Han 用户解析成三课堂教育身份，供 han-auth 从本地登录态换发兼容凭证。
 *
 * <p>只读 {@code edu_person} 与 {@code edu_school}，不查 {@code sys_user_social}：
 * 本地账号不存在任何外部身份绑定，要求绑定行存在会让本地教师直接登不进去。
 *
 * <p>教师和学生都可解析为教育身份；学生令牌的实际业务入口由管理端网关与校端服务端共同收敛，
 * 不能仅凭签发成功获得教师写操作权限。
 */
@Service
@RequiredArgsConstructor
public class LegacyClassroomIdentityService {

    private final LegacyCompatProperties properties;
    private final LegacyDirectoryService directoryService;

    /** 返回当前账号全部有效教育身份，供 H5/App 在签发课堂凭证前选择身份。 */
    public List<ClassroomIdentityVO> list(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return directoryService.personsByUserId(userId).stream()
                .filter(this::isActive)
                .map(this::toIdentity)
                .toList();
    }

    public ClassroomIdentityVO resolve(Long userId) {
        return list(userId).stream()
                .filter(ClassroomIdentityVO::isLoginAllowed)
                .findFirst()
                .orElse(null);
    }

    /** 指定身份必须属于当前账号、启用且在当前策略允许范围内。 */
    public ClassroomIdentityVO resolve(Long userId, String identityId) {
        if (identityId == null || identityId.isBlank()) {
            return resolve(userId);
        }
        return list(userId).stream()
                .filter(item -> identityId.trim().equals(item.getIdentityId()))
                .filter(ClassroomIdentityVO::isLoginAllowed)
                .findFirst()
                .orElse(null);
    }

    private boolean isActive(EduPersonPo person) {
        return person.getStatus() != null && person.getStatus() == 0
                && (person.getLeaveFlag() == null || person.getLeaveFlag() == 0);
    }

    private ClassroomIdentityVO toIdentity(EduPersonPo person) {
        EduSchoolPo school = directoryService.schoolById(person.getSchoolId());
        String roleType = properties.roleTypeOf(person.getPersonType());
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
                .build();
    }
}
