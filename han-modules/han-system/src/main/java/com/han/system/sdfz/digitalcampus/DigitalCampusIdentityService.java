package com.han.system.sdfz.digitalcampus;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.api.system.domain.DigitalCampusUserSyncDTO;
import com.han.api.system.domain.UserVO;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.PasswordUtil;
import com.han.common.core.util.XuIdUtil;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.system.converter.SysUserApiConverter;
import com.han.system.domain.po.SysUserPo;
import com.han.system.domain.po.SysUserSocialPo;
import com.han.system.mapper.SysUserMapper;
import com.han.system.service.SysUserSocialService;
import com.han.system.sdfz.education.EducationAccountIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 数字校园身份到 Han 用户的幂等映射服务。
 *
 * <p>外部身份首次登录时创建无管理角色、无可用本地密码的普通用户；后续登录只刷新上游主责字段，
 * 不覆盖 Han 用户状态、角色或数据权限。</p>
 */
@Service
@RequiredArgsConstructor
public class DigitalCampusIdentityService {

    static final String PROVIDER = "digital-campus";

    private final SysUserMapper userMapper;
    private final SysUserSocialService socialService;
    private final SysUserApiConverter userConverter;
    private final ObjectMapper objectMapper;
    private final DigitalCampusEducationSyncService educationSyncService;
    private final EducationAccountIdentityService accountIdentityService;

    @Transactional(rollbackFor = Exception.class)
    public UserVO syncCurrentUser(DigitalCampusUserSyncDTO dto) {
        validate(dto);

        List<SysUserSocialPo> matches = socialService.listByProviderOpenId(PROVIDER, dto.getExternalUserId()).stream()
                .filter(binding -> Objects.equals(dto.getTenantId(), binding.getTenantId()))
                .toList();
        if (matches.size() > 1) {
            throw new BusinessException("数字校园身份映射冲突，请联系管理员");
        }

        SysUserPo user;
        if (matches.isEmpty()) {
            user = createUser(dto);
            socialService.bind(user.getId(), dto.getTenantId(), PROVIDER, dto.getExternalUserId(),
                    null, safeText(dto.getUserName(), 100), null);
        } else {
            user = TenantHelper.ignore(() -> userMapper.selectById(matches.getFirst().getUserId()));
            if (user == null) {
                throw new BusinessException("数字校园身份关联的 Han 用户不存在");
            }
            refreshUpstreamFields(user, dto);
            socialService.bind(user.getId(), dto.getTenantId(), PROVIDER, dto.getExternalUserId(),
                    null, safeText(dto.getUserName(), 100), null);
        }

        socialService.updateExtra(user.getId(), PROVIDER, snapshot(dto));
        educationSyncService.sync(dto, user.getId());
        accountIdentityService.syncFromAccount(user);
        return userConverter.toApiUserVO(user);
    }

    public Map<String, Object> getExternalProfile(Long userId) {
        SysUserSocialPo binding = socialService.getByUserAndProvider(userId, PROVIDER);
        if (binding == null) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> stored = binding.getExtra() != null && !binding.getExtra().isBlank()
                    ? objectMapper.readValue(binding.getExtra(), LinkedHashMap.class)
                    : Map.of();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("provider", PROVIDER);
            result.put("externalUserId", binding.getOpenId());
            result.putAll(stored);
            return result;
        } catch (JacksonException e) {
            throw new BusinessException("数字校园身份快照格式无效");
        }
    }

    private SysUserPo createUser(DigitalCampusUserSyncDTO dto) {
        String username = externalUsername(dto.getExternalUserId());
        SysUserPo collision = TenantHelper.ignore(() -> userMapper.selectOne(new LambdaQueryWrapper<SysUserPo>()
                .eq(SysUserPo::getTenantId, dto.getTenantId())
                .eq(SysUserPo::getUsername, username)
                .last("LIMIT 1")));
        if (collision != null) {
            throw new BusinessException("数字校园账号映射冲突，请联系管理员");
        }

        SysUserPo user = new SysUserPo();
        user.setTenantId(dto.getTenantId());
        user.setUsername(username);
        user.setNickname(displayName(dto, username));
        user.setPhone(normalPhone(dto.getPhone()));
        user.setPassword(PasswordUtil.encode(XuIdUtil.uuid() + "Aa1!"));
        user.setStatus(0);
        user.setPwdUpdateTime(LocalDateTime.now());
        user.setPwdResetFlag(0);
        user.setRemark("数字校园自动建账");
        TenantHelper.ignore(() -> userMapper.insert(user));
        return user;
    }

    private void refreshUpstreamFields(SysUserPo user, DigitalCampusUserSyncDTO dto) {
        boolean changed = false;
        String nickname = displayName(dto, user.getUsername());
        if (!Objects.equals(nickname, user.getNickname())) {
            user.setNickname(nickname);
            changed = true;
        }
        String phone = normalPhone(dto.getPhone());
        if (phone != null && !Objects.equals(phone, user.getPhone())) {
            user.setPhone(phone);
            changed = true;
        }
        if (changed) {
            TenantHelper.ignore(() -> userMapper.updateById(user));
        }
    }

    private String snapshot(DigitalCampusUserSyncDTO dto) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("externalIdentityId", dto.getExternalIdentityId());
        snapshot.put("userName", dto.getUserName());
        snapshot.put("identityName", dto.getIdentityName());
        snapshot.put("roleType", dto.getRoleType());
        snapshot.put("schoolId", dto.getSchoolId());
        snapshot.put("schoolName", dto.getSchoolName());
        snapshot.put("branchId", dto.getBranchId());
        snapshot.put("branchName", dto.getBranchName());
        snapshot.put("isSchool", dto.getIsSchool());
        snapshot.put("areaCode", dto.getAreaCode());
        snapshot.put("duties", dto.getDuties() != null ? dto.getDuties() : List.of());
        snapshot.put("classes", dto.getClasses() != null ? dto.getClasses() : List.of());
        snapshot.put("lastSyncAt", OffsetDateTime.now().toString());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException e) {
            throw new BusinessException("数字校园身份快照生成失败");
        }
    }

    private static void validate(DigitalCampusUserSyncDTO dto) {
        if (dto == null || dto.getTenantId() == null || dto.getTenantId() <= 0
                || isBlank(dto.getExternalUserId()) || isBlank(dto.getExternalIdentityId())) {
            throw new BusinessException("数字校园用户同步参数不完整");
        }
    }

    private static String displayName(DigitalCampusUserSyncDTO dto, String fallback) {
        return !isBlank(dto.getUserName()) ? safeText(dto.getUserName(), 50) : fallback;
    }

    private static String normalPhone(String phone) {
        if (phone == null || !phone.matches("\\d{11}")) {
            return null;
        }
        return phone;
    }

    static String externalUsername(String externalUserId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(externalUserId.getBytes(StandardCharsets.UTF_8));
            return "dc_" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }

    private static String safeText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
