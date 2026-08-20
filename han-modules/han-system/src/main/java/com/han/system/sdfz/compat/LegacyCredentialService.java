package com.han.system.sdfz.compat;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.ClassroomTokenCodec;
import com.han.common.core.util.PasswordUtil;
import com.han.system.domain.po.SysUserPo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 旧登录页到兼容凭证的本地账号链路（C1 图形验证码 / C2 登录 / C3 当前用户）。
 *
 * <p>C2 只返回中间态凭证，前端随即调 C3；C3 组装 {@code roles[]} 并签发正式凭证，
 * 从这一刻起该凭证既是身份凭证又是 AES 密钥源。
 *
 * <p>本期只放行教师。学生走到这条链路会拿到
 * {@link LegacyTokenIssuer#STUDENT_LOGIN_UNSUPPORTED} 这条明确文案，
 * 但这不影响他们出现在兼容目录的名册与课程参与数据里。
 */
@Service
@RequiredArgsConstructor
public class LegacyCredentialService {

    private static final String CAPTCHA_KEY_PREFIX = "sdfz:compat:captcha:";
    private static final String LOGIN_FAIL_PREFIX = "sdfz:compat:login-fail:";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    /** 验证码 key 由前端传入并拼进 Redis key，只接受安全字符。 */
    private static final String SAFE_KEY = "[A-Za-z0-9_-]{1,64}";
    private static final String GENERIC_LOGIN_FAILURE = "用户名或密码错误";

    private final LegacyCompatProperties properties;
    private final LegacyDirectoryService directoryService;
    private final LegacyTokenIssuer tokenIssuer;
    private final StringRedisTemplate redisTemplate;
    private final LegacyCipher cipher;

    /** C1：图形验证码，前端按 {@code code == 0 && success} 判定，与其它接口不同。 */
    public LegacyPayload captcha(String key) {
        String checkKey = safeKey(key);
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(150, 40, 4, 30);
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + checkKey, captcha.getCode(), CAPTCHA_TTL);
        return LegacyPayload.same(captcha.getImageBase64Data()).withUiCode(LegacyPayload.UI_CAPTCHA_OK);
    }

    /** C2：账号密码登录，返回中间态凭证。 */
    public LegacyPayload login(LegacyRequest request) {
        String loginName = decryptUiCredential(request.firstText("phone", "username", "account", "loginName"));
        String password = decryptUiCredential(request.text("password"));
        if (loginName == null || password == null) {
            throw new BusinessException(GENERIC_LOGIN_FAILURE);
        }
        verifyCaptcha(request.text("captcha"), request.text("checkKey"));
        checkLockout(loginName);

        SysUserPo user = directoryService.userByLoginName(loginName);
        if (user == null || !PasswordUtil.matches(password, user.getPassword())) {
            recordFailure(loginName);
            throw new BusinessException(GENERIC_LOGIN_FAILURE);
        }
        if (user.getStatus() == null || user.getStatus() != 0) {
            recordFailure(loginName);
            throw new BusinessException("账号已停用，请联系管理员");
        }

        EduPersonPo person = directoryService.personByUserId(user.getId());
        if (person == null) {
            recordFailure(loginName);
            throw new BusinessException("当前账号未开通三个课堂身份");
        }
        // 这里判的是 Han 的启用状态；已逻辑删除的人员根本查不出来，不需要另判 del_flag。
        if (person.getStatus() == null || person.getStatus() != 0) {
            recordFailure(loginName);
            throw new BusinessException("当前账号的三个课堂身份已停用");
        }

        clearFailures(loginName);
        if (!tokenIssuer.canIssueFor(person)) {
            throw new BusinessException(LegacyTokenIssuer.STUDENT_LOGIN_UNSUPPORTED);
        }
        LegacyTokenIssuer.IssuedToken issued = tokenIssuer.issueInterim(person);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("token", issued.token());
        value.put("expiresIn", issued.expiresIn());
        return LegacyPayload.same(value);
    }

    /**
     * C3：当前用户身份，U4 的核心。
     *
     * <p>{@code roles} 为空数组时旧前端会静默登录失败且没有任何提示，因此这里宁可抛业务异常。
     */
    public LegacyPayload currentUser(LegacyRequest request) {
        if (request.token() == null) {
            throw new BusinessException("登录状态已失效，请重新登录");
        }
        ClassroomTokenCodec.VerifiedToken verified = tokenIssuer.verify(request.token());
        List<EduPersonPo> active = activeIdentities(verified);
        if (active.isEmpty()) {
            throw new BusinessException("当前账号没有可用的三个课堂身份");
        }
        // 学生身份留在目录里，但不进 roles[]，否则旧前端会拿到一个没有登录能力的身份。
        List<EduPersonPo> persons = active.stream().filter(tokenIssuer::canIssueFor).toList();
        if (persons.isEmpty()) {
            throw new BusinessException(LegacyTokenIssuer.STUDENT_LOGIN_UNSUPPORTED);
        }

        EduPersonPo primary = persons.getFirst();
        LegacyTokenIssuer.IssuedToken issued = tokenIssuer.issueSession(primary);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("roles", persons.stream().map(directoryService::roleOf).toList());
        value.put("accessToken", issued.token());
        value.put("wxPhoneNumber", primary.getPhone() != null ? primary.getPhone() : "");
        value.put("userId", directoryService.externalUserId(primary));
        value.put("userName", primary.getPersonName() != null ? primary.getPersonName() : "");
        return LegacyPayload.same(value);
    }

    private List<EduPersonPo> activeIdentities(ClassroomTokenCodec.VerifiedToken verified) {
        Long hanUserId = claimAsLong(verified, "hanUserId");
        List<EduPersonPo> persons = directoryService.personsByUserId(hanUserId);
        if (persons.isEmpty()) {
            EduPersonPo byIdentity = directoryService.personById(claimAsLong(verified, "identityId"));
            persons = byIdentity != null ? List.of(byIdentity) : List.of();
        }
        return persons.stream()
                .filter(person -> person.getStatus() != null && person.getStatus() == 0)
                .toList();
    }

    private void verifyCaptcha(String captcha, String checkKey) {
        if (!properties.isCaptchaEnabled()) {
            return;
        }
        if (captcha == null || checkKey == null) {
            throw new BusinessException("验证码不能为空");
        }
        String redisKey = CAPTCHA_KEY_PREFIX + safeKey(checkKey);
        String expected = redisTemplate.opsForValue().get(redisKey);
        redisTemplate.delete(redisKey);
        if (expected == null) {
            throw new BusinessException("验证码已过期");
        }
        if (!expected.equalsIgnoreCase(captcha.trim())) {
            throw new BusinessException("验证码错误");
        }
    }

    /**
     * 旧校端会先分别加密账号和密码，再加密整个 param 信封；管理端测试或较早调用方可能传明文。
     */
    private String decryptUiCredential(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return cipher.decrypt(value, null);
        } catch (RuntimeException ignored) {
            return value.trim();
        }
    }

    private void checkLockout(String loginName) {
        String value = redisTemplate.opsForValue().get(failureKey(loginName));
        if (value == null) {
            return;
        }
        int failures;
        try {
            failures = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return;
        }
        if (failures >= properties.getMaxLoginAttempts()) {
            throw new BusinessException("登录失败次数过多，请稍后再试");
        }
    }

    private void recordFailure(String loginName) {
        String key = failureKey(loginName);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(properties.getLoginLockSeconds()));
        }
    }

    private void clearFailures(String loginName) {
        redisTemplate.delete(failureKey(loginName));
    }

    private String failureKey(String loginName) {
        return LOGIN_FAIL_PREFIX + properties.getTenantId() + ":"
                + ClassroomTokenCodec.sha256(loginName.toLowerCase(java.util.Locale.ROOT));
    }

    private static Long claimAsLong(ClassroomTokenCodec.VerifiedToken verified, String name) {
        Object value = verified.claims().get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String safeKey(String key) {
        if (key == null || !key.matches(SAFE_KEY)) {
            throw new BusinessException("验证码标识不合法");
        }
        return key;
    }
}
