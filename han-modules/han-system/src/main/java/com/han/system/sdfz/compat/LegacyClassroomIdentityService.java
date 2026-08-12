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
 * <p>本期只解析可登录的身份（教师）。学生仍然出现在兼容目录的名册与课程参与数据里，
 * 但这里返回 {@code null}，让上游给出"暂不支持学生登录"的明确结论。
 */
@Service
@RequiredArgsConstructor
public class LegacyClassroomIdentityService {

    private final LegacyCompatProperties properties;
    private final LegacyDirectoryService directoryService;

    public ClassroomIdentityVO resolve(Long userId) {
        List<EduPersonPo> persons = directoryService.personsByUserId(userId);
        EduPersonPo person = persons.stream()
                .filter(item -> properties.canIssueToken(item.getPersonType()))
                .findFirst()
                .orElse(null);
        if (person == null) {
            return null;
        }

        EduSchoolPo school = directoryService.schoolById(person.getSchoolId());
        String roleType = properties.roleTypeOf(person.getPersonType());
        return ClassroomIdentityVO.builder()
                .userId(directoryService.externalUserId(person))
                .identityId(String.valueOf(person.getId()))
                .userName(person.getPersonName())
                .roleType(roleType)
                .schoolId(person.getSchoolId() == null ? "" : String.valueOf(person.getSchoolId()))
                .schoolName(school != null && school.getSchoolName() != null ? school.getSchoolName() : "")
                .status(person.getStatus() != null ? person.getStatus() : 0)
                .roles(List.of(roleType, person.getPersonType()))
                .build();
    }
}
