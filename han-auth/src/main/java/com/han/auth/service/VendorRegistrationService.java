package com.han.auth.service;

import com.han.api.open.OpenServiceClient;
import com.han.api.open.domain.OpenVendorApplicationCreateDTO;
import com.han.api.open.domain.OpenVendorApplicationStatusVO;
import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.OpenVendorAccountCreateDTO;
import com.han.auth.config.SecurityProperties;
import com.han.auth.domain.VendorPublicRegisterDTO;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.constant.Constants;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.HanSecureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 厂商公开注册编排：auth 验证码/RSA → system 账号 → open 申请。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorRegistrationService {

    private static final long PLATFORM_TENANT_ID = 1L;

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;
    private final CaptchaSettingService captchaSettingService;
    private final SystemServiceClient systemServiceClient;
    private final OpenServiceClient openServiceClient;

    public String register(VendorPublicRegisterDTO dto) {
        if (dto == null) {
            throw new BusinessException("厂商注册信息不能为空");
        }
        validateCaptcha(dto);
        String name = required(dto.getName(), "厂商名称不能为空");
        String qualificationNo = required(dto.getQualificationNo(), "统一社会信用代码不能为空");
        String contactName = required(dto.getContactName(), "联系人姓名不能为空");
        String contactPhone = required(dto.getContactPhone(), "联系电话不能为空");
        String accountPhone = required(dto.getPhone(), "账号手机号不能为空");
        String username = required(dto.getUsername(), "登录用户名不能为空");
        String password = decryptPassword(dto.getEncryptedPassword());

        OpenVendorAccountCreateDTO account = new OpenVendorAccountCreateDTO();
        account.setTenantId(PLATFORM_TENANT_ID);
        account.setUsername(username);
        account.setNickname(StringUtils.hasText(dto.getNickname())
                ? dto.getNickname().trim() : contactName);
        account.setPassword(password);
        account.setPhone(accountPhone);
        account.setEmail(dto.getEmail());

        R<Long> accountResult;
        try {
            accountResult = systemServiceClient.createOpenVendorAccount(account);
        } catch (Exception e) {
            throw new BusinessException("提交状态未确认，请使用原账号重试");
        }
        if (accountResult == null) {
            throw new BusinessException("提交状态未确认，请使用原账号重试");
        }
        if (accountResult.getCode() != Constants.SUCCESS) {
            throw new BusinessException(safeAccountMessage(accountResult == null ? null : accountResult.getMsg()));
        }
        if (accountResult.getData() == null) {
            throw new BusinessException("提交状态未确认，请使用原账号重试");
        }

        Long accountUserId = accountResult.getData();
        OpenVendorApplicationCreateDTO application = new OpenVendorApplicationCreateDTO();
        application.setAccountUserId(accountUserId);
        application.setName(name);
        application.setQualificationNo(qualificationNo);
        application.setIndustry(dto.getIndustry());
        application.setContactName(contactName);
        application.setContactPhone(contactPhone);
        application.setContactEmail(dto.getContactEmail());
        application.setWebsite(dto.getWebsite());
        application.setApplyReason(dto.getApplyReason());

        R<String> result;
        try {
            result = openServiceClient.createPortalApplication(application);
        } catch (Exception e) {
            throw new BusinessException("提交状态未确认，请使用原账号重试");
        }
        if (result == null || result.getCode() == Constants.SUCCESS && !StringUtils.hasText(result.getData())) {
            throw new BusinessException("提交状态未确认，请使用原账号重试");
        }
        if (result.getCode() != Constants.SUCCESS) {
            compensateAccount(accountUserId);
            throw new BusinessException(safeOpenMessage(result.getMsg()));
        }
        return result.getData();
    }

    public OpenVendorApplicationStatusVO queryStatus(String applicationNo, String contactPhone) {
        String no = required(applicationNo, "申请编号不能为空");
        String phone = required(contactPhone, "联系电话不能为空");
        try {
            R<OpenVendorApplicationStatusVO> result = openServiceClient.queryPortalApplication(no, phone);
            if (result == null || result.getCode() != Constants.SUCCESS || result.getData() == null) {
                throw new BusinessException("申请不存在或校验信息不匹配");
            }
            return result.getData();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("厂商申请状态内部查询异常: exception={}, cause={}",
                    e.getClass().getName(),
                    e.getCause() == null ? "none" : e.getCause().getClass().getName());
            throw new BusinessException("申请不存在或校验信息不匹配");
        }
    }

    private void validateCaptcha(VendorPublicRegisterDTO dto) {
        if (!captchaSettingService.isCaptchaEnabled()) {
            return;
        }
        String code = required(dto.getCaptchaCode(), "验证码不能为空");
        String uuid = required(dto.getCaptchaUuid(), "验证码标识不能为空");
        String key = CacheConstants.CAPTCHA_KEY + uuid;
        String expected = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        if (!StringUtils.hasText(expected) || !expected.equalsIgnoreCase(code)) {
            throw new BusinessException("验证码错误或已过期");
        }
    }

    private String safeAccountMessage(String message) {
        if (StringUtils.hasText(message)
                && (message.contains("用户名") || message.contains("手机号")
                || message.contains("密码") || message.contains("开放平台厂商角色"))) {
            return message;
        }
        return "厂商账号创建失败，请稍后重试";
    }

    private String safeOpenMessage(String message) {
        if (StringUtils.hasText(message)
                && (message.contains("厂商名称") || message.contains("统一社会信用代码")
                || message.contains("厂商账号") || message.contains("申请"))) {
            return message;
        }
        return "厂商申请创建失败，请稍后重试";
    }

    private String decryptPassword(String encryptedPassword) {
        if (!securityProperties.isRegistrationKeyReady()) {
            throw new BusinessException("注册密码加密配置不完整，请联系管理员");
        }
        try {
            return HanSecureUtil.rsaDecrypt(encryptedPassword, securityProperties.getPrivateKey());
        } catch (Exception e) {
            throw new BusinessException("密码必须使用注册公钥加密");
        }
    }

    private void compensateAccount(Long userId) {
        try {
            R<Void> result = systemServiceClient.compensateOpenVendorAccount(userId);
            if (result == null || result.getCode() != Constants.SUCCESS) {
                log.warn("厂商申请失败，禁用账号补偿未确认: userId={}", userId);
            }
        } catch (Exception e) {
            log.warn("厂商申请失败，禁用账号补偿调用异常: userId={}", userId, e);
        }
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }
}
