package com.han.system.domain.vo;

import lombok.Builder;

import java.util.Set;

/**
 * 当前登录用户信息（只读值对象）
 */
@Builder
public record CurrentUserVO(
        Long userId,
        Long tenantId,
        Long deptId,
        String username,
        String nickname,
        String avatar,
        String phone,
        String email,
        Set<String> roles,
        Set<String> permissions,
        boolean identityScoped,
        Long identityId,
        Long schoolId,
        String schoolName,
        String personType,
        String dutyCode,
        String dutyName,
        String identityDisplayName
) {
}
