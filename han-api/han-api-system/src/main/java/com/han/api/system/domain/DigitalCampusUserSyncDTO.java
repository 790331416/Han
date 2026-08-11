package com.han.api.system.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 数字校园当前用户即时同步参数。
 *
 * <p>只包含已确认的身份字段，不包含数字校园原始 Token。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DigitalCampusUserSyncDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private String externalUserId;
    private String externalIdentityId;
    private String userName;
    private String phone;
    private String identityName;
    private String roleType;
    private String schoolId;
    private String schoolName;
    private String branchId;
    private String branchName;
    private String isSchool;
    private String areaCode;
    private List<Duty> duties;
    private List<ClassMembership> classes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Duty implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String pkId;
        private String roleType;
        private String positionName;
        private String itemText;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassMembership implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String branchId;
        private String branchName;
        private String classRoleId;
        private String name;
        private String schoolId;
        private String schoolName;
        private String schoolLevel;
        private String areaCode;
        private String eduDepartId;
        private String eduDepartName;
        private String cityEduDepartId;
        private String cityEduDepartName;
        private String countyEduDepartId;
        private String countyEduDepartName;
        private String townEduDepartId;
        private String townEduDepartName;
    }
}
