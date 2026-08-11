package com.han.auth.sdfz.digitalcampus;

import com.han.auth.domain.LoginVO;

/**
 * 数字校园换票结果。
 */
public record DigitalCampusLoginVO(LoginVO login, ExternalIdentity externalIdentity) {

    public record ExternalIdentity(
            String externalUserId,
            String identityId,
            String userName,
            String identityName,
            String roleType,
            String schoolId,
            String schoolName,
            String branchId,
            String branchName,
            String areaCode) {
    }
}
