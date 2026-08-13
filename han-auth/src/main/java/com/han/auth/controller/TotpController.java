package com.han.auth.controller;

import com.han.api.system.SystemServiceClient;
import com.han.api.system.domain.UserVO;
import com.han.auth.service.TotpService;
import com.han.common.core.constant.Constants;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.PasswordUtil;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 2FA / TOTP 两步验证控制器
 *
 * <p>本类不走权限串鉴权：所有接口都只操作调用者本人的 2FA，用户标识一律取自登录态
 * （{@code SecurityContextHolder.getUserId()}），不接受外部传入，因此不存在越权面。
 * 访问控制由「网关校验 Token（{@code /auth/totp} 不在网关白名单）+ 方法内校验 userId」
 * 两层构成，用 {@link PermissionExempt} 把这个事实显式声明出来，
 * 以便 {@code PermissionCheckPostProcessor} 能扫到本类——否则整个类对启动门禁隐形，
 * 新增方法漏写登录校验时不会有任何提示。
 */
@Slf4j
@RestController
@RequestMapping("/auth/totp")
@RequiredArgsConstructor
public class TotpController {

    private final TotpService totpService;
    private final SystemServiceClient systemServiceClient;

    /**
     * 生成 TOTP 绑定信息（密钥 + 二维码）
     * <p>用户在个人中心点击"启用两步验证"时调用
     */
    @GetMapping("/setup")
    @PermissionExempt("本人登录态接口，方法内校验 userId 后只操作调用者自己的 2FA，不接受外部传入的用户标识")
    public R<Map<String, String>> setup() {
        Long userId = SecurityContextHolder.getUserId();
        String username = SecurityContextHolder.getUsername();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }

        String secret = totpService.generateSecret();
        String qrCode = totpService.generateQrCodeBase64(secret, username);
        String otpAuthUrl = totpService.generateOtpAuthUrl(secret, username);

        return R.ok(Map.of(
                "secret", secret,
                "qrCode", qrCode,
                "otpAuthUrl", otpAuthUrl
        ));
    }

    /**
     * 确认绑定 TOTP（验证码校验通过后保存密钥）
     */
    @PostMapping("/bind")
    @PermissionExempt("本人登录态接口，方法内校验 userId 后只操作调用者自己的 2FA，不接受外部传入的用户标识")
    public R<Void> bind(@RequestBody Map<String, String> body) {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }

        String secret = body.get("secret");
        String code = body.get("code");

        if (secret == null || secret.isBlank()) {
            throw new BusinessException("密钥不能为空");
        }
        if (!totpService.verifyCode(secret, code)) {
            throw new BusinessException("验证码错误，请重新输入");
        }

        // 通过 RPC 保存 TOTP 密钥到用户表
        systemServiceClient.updateTotpSecret(userId, secret);
        log.info("用户[{}]成功绑定 2FA", userId);
        return R.ok();
    }

    /**
     * 解绑 TOTP
     */
    @PostMapping("/unbind")
    @PermissionExempt("本人登录态接口，方法内校验 userId 并额外要求当前密码，只解绑调用者自己的 2FA")
    public R<Void> unbind(@RequestBody Map<String, String> body) {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }

        // 解绑前需要验证当前密码
        String password = body.get("password");
        if (password == null || password.isBlank()) {
            throw new BusinessException("请输入当前密码");
        }

        R<UserVO> userResult = systemServiceClient.getUserById(userId);
        if (userResult.getCode() != Constants.SUCCESS || userResult.getData() == null) {
            throw new BusinessException("用户不存在");
        }

        UserVO user = userResult.getData();
        if (!PasswordUtil.matches(password, user.getPassword())) {
            throw new BusinessException("密码错误");
        }

        systemServiceClient.updateTotpSecret(userId, null);
        log.info("用户[{}]解绑 2FA", userId);
        return R.ok();
    }

    /**
     * 获取当前用户 2FA 状态
     */
    @GetMapping("/status")
    @PermissionExempt("本人登录态接口，方法内校验 userId 后只读取调用者自己的 2FA 状态")
    public R<Map<String, Object>> status() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }

        R<UserVO> userResult = systemServiceClient.getUserById(userId);
        if (userResult.getCode() != Constants.SUCCESS || userResult.getData() == null) {
            throw new BusinessException("用户不存在");
        }

        boolean enabled = userResult.getData().getTotpEnabled() != null
                && userResult.getData().getTotpEnabled() == 1;
        return R.ok(Map.of("enabled", enabled));
    }
}
