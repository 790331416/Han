package com.han.auth.sdfz.digitalcampus;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.ClassroomIdentityVO;
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
        // 数字校园已选身份按该身份签发：外部 identityId 已通过精确接口映射为本地 edu_person.id，
        // 统一走身份感知出口，LoginUser.identityId 与本地身份一致，多身份不再二次选择。
        LoginVO login = authService.issueLoginForIdentity(synchronizedIdentity.user(), ClientType.PC, false,
                synchronizedIdentity.localIdentityId());
        return new DigitalCampusLoginVO(login, new DigitalCampusLoginVO.ExternalIdentity(
                identity.userId(), identity.identityId(), identity.userName(), identity.identityName(),
                identity.roleType(), identity.schoolId(), identity.schoolName(), identity.branchId(),
                identity.branchName(), identity.areaCode()));
    }

    /**
     * 用数字校园稳定外部身份 ID 精确解析本地教育身份（edu_person.id 与本地 schoolId）。
     *
     * <p>同步以 {@code external_identity_id} 幂等写入 edu_person，因此这里直接按该稳定标识
     * 调用 han-system 的精确查询接口定位本地身份，不再依赖「学校名 + 姓名」这类易受
     * 同名学校 / 学校改名影响的匹配。
     */
    private ClassroomIdentityVO resolveLocalIdentity(Long userId, DigitalCampusProfile.Identity identity) {
        R<ClassroomIdentityVO> result = systemServiceClient.getClassroomIdentityByExternal(
                userId, identity.identityId());
        if (result == null || result.getCode() != Constants.SUCCESS) {
            throw new BusinessException("身份服务暂时不可用，请稍后重试");
        }
        if (result.getData() == null) {
            throw new BusinessException("数字校园身份与本地教育身份不匹配，请联系管理员");
        }
        return result.getData();
    }

    private Long parseIdentityId(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("数字校园身份与本地教育身份不匹配，请联系管理员");
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("数字校园身份与本地教育身份不匹配，请联系管理员");
        }
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

        UserVO user = syncResult.getData();
        ClassroomIdentityVO localIdentity = resolveLocalIdentity(user.getUserId(), identity);
        return new SynchronizedIdentity(user, identity,
                parseIdentityId(localIdentity.getIdentityId()), localIdentity.getSchoolId());
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

    /**
     * 数字校园同步后的聚合结果：Han 用户、外部身份，以及按外部身份 ID 精确解析出的
     * 本地教育身份（{@code edu_person.id}）与本地学校 ID。
     */
    public record SynchronizedIdentity(
            UserVO user,
            DigitalCampusProfile.Identity identity,
            Long localIdentityId,
            String localSchoolId) {
    }
}
