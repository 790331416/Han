package com.han.ai.security;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 凭据脱敏掩码识别器。
 * <p>
 * 用于判定「客户端回传的值是不是我们自己脱敏出去的掩码串」，配合 han-common-web 的
 * {@code SensitiveSerializer} 使用。凡是被判定为掩码的值，一律不得写库、不得当作真实凭据外发。
 * <p>
 * 必须与 {@code SensitiveSerializer.maskCustom(value, prefixKeep, suffixKeep)} 的产出保持一致：
 * <ul>
 *   <li>原值长度 ≤ prefixKeep + suffixKeep 时产出全星号，例如 {@code ********}；</li>
 *   <li>原值更长时产出「前 N 位 + 星号段 + 后 M 位」，例如 {@code sk-1****************abcd}。</li>
 * </ul>
 * 只识别全星号（历史实现的做法）会漏掉第二种形态 —— 而真实 API Key 长度都远超 8 位，
 * 恰恰全部落在第二种形态里，导致掩码串被当成新凭据写库、并被当作真实凭据发给模型服务商。
 * <p>
 * 判定规则（满足任一即视为掩码）：
 * <ol>
 *   <li>整串都是星号；</li>
 *   <li>出现连续 3 个及以上星号 —— 合法的 API Key、Bearer Token、JSON 配置都不会包含这种片段；</li>
 *   <li>整串精确符合 CUSTOM(prefixKeep=4, suffixKeep=4) 的掩码形状，覆盖长度 9~10 时星号段不足 3 位的边界。</li>
 * </ol>
 * 误判的代价是「提示用户重新填写完整凭据」，漏判的代价是「真实凭据被销毁或掩码被当凭据外发」，
 * 因此这里刻意取偏严的判定。
 */
public final class AiCredentialMaskDetector {

    /** 与 AiModelPo.apiKey、AiMcpServerPo.envVars 上 {@code @Sensitive} 的 prefixKeep 保持一致 */
    private static final int CUSTOM_PREFIX_KEEP = 4;
    /** 与 AiModelPo.apiKey、AiMcpServerPo.envVars 上 {@code @Sensitive} 的 suffixKeep 保持一致 */
    private static final int CUSTOM_SUFFIX_KEEP = 4;

    private static final Pattern ALL_ASTERISK = Pattern.compile("^\\*+$");
    private static final Pattern ASTERISK_RUN = Pattern.compile("\\*{3,}");

    private AiCredentialMaskDetector() {
    }

    /**
     * 判定给定值是否是脱敏掩码串。空值返回 false（空值语义由调用方自行决定）。
     */
    public static boolean isMasked(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim();
        return ALL_ASTERISK.matcher(normalized).matches()
                || ASTERISK_RUN.matcher(normalized).find()
                || matchesCustomMaskShape(normalized);
    }

    /**
     * 精确形状匹配：前 {@value #CUSTOM_PREFIX_KEEP} 位与后 {@value #CUSTOM_SUFFIX_KEEP} 位不含星号，
     * 中间区段全部是星号。
     */
    private static boolean matchesCustomMaskShape(String value) {
        int length = value.length();
        int maskStart = CUSTOM_PREFIX_KEEP;
        int maskEnd = length - CUSTOM_SUFFIX_KEEP;
        if (maskEnd <= maskStart) {
            return false;
        }
        for (int index = 0; index < length; index++) {
            boolean insideMaskRegion = index >= maskStart && index < maskEnd;
            if (insideMaskRegion != (value.charAt(index) == '*')) {
                return false;
            }
        }
        return true;
    }
}
