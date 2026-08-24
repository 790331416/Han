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

import java.util.List;
import java.util.Objects;

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
        // 数字校园已选身份按该身份签发：把外部 identityId 映射为本地 edu_person 身份，
        // 统一走身份感知出口，LoginUser.identityId 与本地身份一致，多身份不再二次选择。
        Long localIdentityId = resolveLocalIdentityId(synchronizedIdentity.user().getUserId(), identity);
        LoginVO login = authService.issueLoginForIdentity(synchronizedIdentity.user(), ClientType.PC, false,
                localIdentityId);
        return new DigitalCampusLoginVO(login, new DigitalCampusLoginVO.ExternalIdentity(
                identity.userId(), identity.identityId(), identity.userName(), identity.identityName(),
                identity.roleType(), identity.schoolId(), identity.schoolName(), identity.branchId(),
                identity.branchName(), identity.areaCode()));
    }

    /**
     * 把数字校园选中的外部身份映射为本地教育身份主键（edu_person.id）。
     *
     * <p>同步以 {@code external_identity_id} 幂等写入 edu_person，但 ClassroomIdentityVO 不暴露
     * 该字段，因此这里以「学校名 + 姓名」定位刚同步出的本地身份；同一账号在同一学校只有一条
     * 有效身份（任务书 12 节），学校名命中唯一时直接采用，多命中时再按姓名收窄。
     */
    private Long resolveLocalIdentityId(Long userId, DigitalCampusProfile.Identity identity) {
        R<List<ClassroomIdentityVO>> result = systemServiceClient.listClassroomIdentities(userId);
        if (result == null || result.getCode() != Constants.SUCCESS || result.getData() == null) {
            throw new BusinessException("身份服务暂时不可用，请稍后重试");
        }
        List<ClassroomIdentityVO> identities = result.getData().stream()
                .filter(Objects::nonNull)
                .toList();
        List<ClassroomIdentityVO> schoolMatch = identities.stream()
                .filter(item -> Objects.equals(normalize(item.getSchoolName()), normalize(identity.schoolName())))
                .toList();
        List<ClassroomIdentityVO> candidates = schoolMatch.size() == 1
                ? schoolMatch
                : schoolMatch.stream()
                        .filter(item -> Objects.equals(normalize(item.getUserName()), normalize(identity.userName())))
                        .toList();
        if (candidates.size() != 1) {
            throw new BusinessException("数字校园身份与本地教育身份不匹配，请联系管理员");
        }
        return parseLongOrThrow(candidates.get(0).getIdentityId());
    }

    private Long parseLongOrThrow(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("数字校园身份与本地教育身份不匹配，请联系管理员");
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("数字校园身份与本地教育身份不匹配，请联系管理员");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
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
