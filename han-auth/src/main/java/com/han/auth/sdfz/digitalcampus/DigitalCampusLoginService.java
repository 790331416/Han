package com.han.auth.sdfz.digitalcampus;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.DigitalCampusUserSyncDTO;
import com.han.api.system.domain.UserVO;
import com.han.auth.domain.LoginVO;
import com.han.auth.service.IAuthService;
import com.han.common.core.constant.Constants;
import com.han.common.core.domain.R;
import com.han.common.core.enums.ClientType;
import com.han.common.core.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 数字校园 Token 换取 Han 登录态的编排服务。
 */
@Service
public class DigitalCampusLoginService {

    private final DigitalCampusClient digitalCampusClient;
    private final SystemServiceClient systemServiceClient;
    private final IAuthService authService;
    private final long tenantId;

    public DigitalCampusLoginService(
            DigitalCampusClient digitalCampusClient,
            SystemServiceClient systemServiceClient,
            IAuthService authService,
            @Value("${sdfz.digital-campus.tenant-id:1}") long tenantId) {
        this.digitalCampusClient = digitalCampusClient;
        this.systemServiceClient = systemServiceClient;
        this.authService = authService;
        this.tenantId = tenantId;
    }

    public DigitalCampusLoginVO login(String token, String identityId) {
        SynchronizedIdentity synchronizedIdentity = synchronize(token, identityId);
        DigitalCampusProfile.Identity identity = synchronizedIdentity.identity();
        LoginVO login = authService.issueLoginForUser(synchronizedIdentity.user(), ClientType.PC, false);
        return new DigitalCampusLoginVO(login, new DigitalCampusLoginVO.ExternalIdentity(
                identity.userId(), identity.identityId(), identity.userName(), identity.identityName(),
                identity.roleType(), identity.schoolId(), identity.schoolName(), identity.branchId(),
                identity.branchName(), identity.areaCode()));
    }

    public SynchronizedIdentity synchronize(String token, String identityId) {
        DigitalCampusProfile profile = digitalCampusClient.fetchCurrentUser(token);
        DigitalCampusProfile.Identity identity = profile.selectIdentity(identityId);
        if (identity.userId() == null || identity.userId().isBlank()) {
            throw new BusinessException("数字校园未返回稳定用户标识");
        }

        R<UserVO> syncResult = systemServiceClient.syncDigitalCampusUser(toSyncDto(profile, identity));
        if (syncResult == null || syncResult.getCode() != Constants.SUCCESS || syncResult.getData() == null) {
            String message = syncResult != null ? syncResult.getMsg() : null;
            throw new BusinessException(message != null && !message.isBlank()
                    ? message : "数字校园用户映射失败");
        }

        return new SynchronizedIdentity(syncResult.getData(), identity);
    }

    private DigitalCampusUserSyncDTO toSyncDto(DigitalCampusProfile profile,
                                                DigitalCampusProfile.Identity identity) {
        return DigitalCampusUserSyncDTO.builder()
                .tenantId(tenantId)
                .externalUserId(identity.userId())
                .externalIdentityId(identity.identityId())
                .userName(identity.userName())
                .phone(profile.phone())
                .identityName(identity.identityName())
                .roleType(identity.roleType())
                .schoolId(identity.schoolId())
                .schoolName(identity.schoolName())
                .branchId(identity.branchId())
                .branchName(identity.branchName())
                .isSchool(identity.isSchool())
                .areaCode(identity.areaCode())
                .duties(identity.duties().stream().map(this::toDuty).toList())
                .classes(identity.classes().stream().map(this::toClassMembership).toList())
                .build();
    }

    private DigitalCampusUserSyncDTO.Duty toDuty(DigitalCampusProfile.Duty duty) {
        return DigitalCampusUserSyncDTO.Duty.builder()
                .pkId(duty.pkId())
                .roleType(duty.roleType())
                .positionName(duty.positionName())
                .itemText(duty.itemText())
                .build();
    }

    private DigitalCampusUserSyncDTO.ClassMembership toClassMembership(
            DigitalCampusProfile.ClassMembership item) {
        return DigitalCampusUserSyncDTO.ClassMembership.builder()
                .branchId(item.branchId())
                .branchName(item.branchName())
                .classRoleId(item.classRoleId())
                .name(item.name())
                .schoolId(item.schoolId())
                .schoolName(item.schoolName())
                .schoolLevel(item.schoolLevel())
                .areaCode(item.areaCode())
                .eduDepartId(item.eduDepartId())
                .eduDepartName(item.eduDepartName())
                .cityEduDepartId(item.cityEduDepartId())
                .cityEduDepartName(item.cityEduDepartName())
                .countyEduDepartId(item.countyEduDepartId())
                .countyEduDepartName(item.countyEduDepartName())
                .townEduDepartId(item.townEduDepartId())
                .townEduDepartName(item.townEduDepartName())
                .build();
    }

    public record SynchronizedIdentity(UserVO user, DigitalCampusProfile.Identity identity) {
    }
}
