package com.han.auth.service;

import com.han.auth.domain.SocialUser;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 微信开放平台网页扫码登录（OAuth2 snsapi_login）。
 *
 * <p>凭据从环境变量 / 受控配置读取（不入 Git、不入库明文）：
 * <ul>
 *   <li>appid：{@code social.wechat.app-id} 或环境变量 {@code WECHAT_OPEN_APP_ID}</li>
 *   <li>secret：{@code social.wechat.app-secret} 或环境变量 {@code WECHAT_OPEN_APP_SECRET}</li>
 * </ul>
 * 未配置时 {@link #isConfigured()} 返回 false，登录入口隐藏、接口给出明确业务错误。
 *
 * <p>身份标识：优先 unionid（同一开放平台账号下多应用一致），无 unionid 时回退 openid，
 * 原始 openid 保留在 {@link SocialUser#getRawOpenId()}。
 */
@Slf4j
@Service
public class WeChatOAuthService implements SocialOAuthProvider {

    public static final String PROVIDER = "wechat";

    private static final String AUTHORIZE_URL = "https://open.weixin.qq.com/connect/qrconnect";
    private static final String TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";
    private static final String USERINFO_URL = "https://api.weixin.qq.com/sns/userinfo";

    private final Environment environment;
    private final RestClient restClient;

    public WeChatOAuthService(Environment environment) {
        this(environment, RestClient.create());
    }

    WeChatOAuthService(Environment environment, RestClient restClient) {
        this.environment = environment;
        this.restClient = restClient;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(appId()) && StringUtils.hasText(appSecret());
    }

    @Override
    public String buildAuthorizeUrl(String redirectUri, String state) {
        requireConfigured();
        return AUTHORIZE_URL
                + "?appid=" + encode(appId())
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=snsapi_login"
                + "&state=" + encode(state)
                + "#wechat_redirect";
    }

    @Override
    public SocialUser fetchUser(String code) {
        requireConfigured();
        Map<String, Object> tokenData = getJson(TOKEN_URL
                + "?appid=" + encode(appId())
                + "&secret=" + encode(appSecret())
                + "&code=" + encode(code)
                + "&grant_type=authorization_code");
        if (tokenData == null || tokenData.get("access_token") == null) {
            // errcode/errmsg 不含凭据，可安全记录
            log.warn("微信授权码换取 access_token 失败: errcode={}, errmsg={}",
                    tokenData != null ? tokenData.get("errcode") : null,
                    tokenData != null ? tokenData.get("errmsg") : null);
            throw new BusinessException("微信授权失败，请重新扫码");
        }

        String accessToken = String.valueOf(tokenData.get("access_token"));
        String openId = textOf(tokenData.get("openid"));
        String unionId = textOf(tokenData.get("unionid"));

        String nickname = "";
        String avatar = "";
        try {
            Map<String, Object> userInfo = getJson(USERINFO_URL
                    + "?access_token=" + encode(accessToken)
                    + "&openid=" + encode(openId)
                    + "&lang=zh_CN");
            if (userInfo != null && userInfo.get("errcode") == null) {
                nickname = textOf(userInfo.get("nickname"));
                avatar = textOf(userInfo.get("headimgurl"));
                if (!StringUtils.hasText(unionId)) {
                    unionId = textOf(userInfo.get("unionid"));
                }
            }
        } catch (Exception e) {
            // 昵称头像获取失败不阻断登录，仅少展示信息
            log.warn("获取微信用户资料失败，继续以 openid 完成登录", e);
        }

        if (!StringUtils.hasText(openId) && !StringUtils.hasText(unionId)) {
            throw new BusinessException("微信授权失败，未获取到用户标识");
        }

        boolean useUnionId = StringUtils.hasText(unionId);
        return SocialUser.builder()
                .provider(PROVIDER)
                .openId(useUnionId ? unionId : openId)
                .rawOpenId(openId)
                .nickname(nickname)
                .avatar(avatar)
                .build();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BusinessException("微信登录未配置，请联系管理员在服务端配置 WECHAT_OPEN_APP_ID/WECHAT_OPEN_APP_SECRET");
        }
    }

    /**
     * 微信 sns 接口返回 JSON 但 Content-Type 常为 text/plain，统一按文本取回后 Jackson 解析。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getJson(String url) {
        String body = restClient.get().uri(url).retrieve().body(String.class);
        if (body == null || body.isBlank()) {
            return null;
        }
        return XuJsonUtil.parseObject(body, Map.class);
    }

    private String appId() {
        return firstText(environment.getProperty("social.wechat.app-id"), environment.getProperty("WECHAT_OPEN_APP_ID"));
    }

    private String appSecret() {
        return firstText(environment.getProperty("social.wechat.app-secret"), environment.getProperty("WECHAT_OPEN_APP_SECRET"));
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String textOf(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private String encode(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }
}
