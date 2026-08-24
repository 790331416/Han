package com.han.auth.sdfz.digitalcampus;

import com.han.common.core.exception.BusinessException;

import java.util.List;

/**
 * 数字校园当前用户的已确认字段，不保存原始 Token。
 */
public record DigitalCampusProfile(String phone, List<Identity> identities) {

    public DigitalCampusProfile {
        phone = phone != null ? phone : "";
        identities = identities != null ? List.copyOf(identities) : List.of();
    }

    public Identity selectIdentity(String identityId) {
        if (identityId != null && !identityId.isBlank()) {
            String selected = identityId.trim();
            return identities.stream()
                    .filter(identity -> selected.equals(identity.identityId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("指定的数字校园身份不可用"));
        }
        if (identities.size() == 1) {
            return identities.getFirst();
        }
        throw new BusinessException("请选择数字校园登录身份");
    }

    public record Identity(
            String userId,
            String userName,
            String identityId,
            String identityName,
            String roleType,
            String schoolId,
            String schoolName,
            String branchId,
            String branchName,
            String isSchool,
            String areaCode,
            List<Duty> duties,
            List<ClassMembership> classes) {

        public Identity {
            duties = duties != null ? List.copyOf(duties) : List.of();
            classes = classes != null ? List.copyOf(classes) : List.of();
        }
    }

    public record Duty(String pkId, String roleType, String positionName, String itemText) {
    }

    public record ClassMembership(
            String branchId,
            String branchName,
            String classRoleId,
            String name,
            String schoolId,
            String schoolName,
            String schoolLevel,
            String areaCode,
            String eduDepartId,
            String eduDepartName,
            String cityEduDepartId,
            String cityEduDepartName,
            String countyEduDepartId,
            String countyEduDepartName,
            String townEduDepartId,
            String townEduDepartName) {
    }
}
