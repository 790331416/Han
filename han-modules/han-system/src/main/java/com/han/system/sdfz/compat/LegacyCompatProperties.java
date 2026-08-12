package com.han.system.sdfz.compat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 三课堂兼容目录与兼容凭证的运行期配置。
 *
 * <p>所有对端契约常量（匿名 AES 密钥、签名密钥、旧编码取值映射）都放在配置里，
 * 使得口径变更不需要改代码，也避免把密钥写进仓库。
 */
@Data
@Component
@ConfigurationProperties(prefix = "sdfz.compat")
public class LegacyCompatProperties {

    /** 兼容层总开关，关闭时所有兼容端点直接返回业务失败信封。 */
    private boolean enabled = false;

    /** 兼容目录读取与本地账号所属租户。 */
    private long tenantId = 1L;

    /** 兼容凭证 HS256 签名密钥，需与 Han Gateway 的 classroom-gateway.token-secret 一致。 */
    private String tokenSecret = "";

    /** 正式兼容凭证有效期，旧前端无静默续期，取上限可减少会话中途换 Token。 */
    private long tokenTtlSeconds = 3600L;

    /** 登录接口返回的中间态凭证有效期，前端拿到后会立刻换取正式凭证。 */
    private long interimTokenTtlSeconds = 300L;

    /** 未登录请求的 AES 密钥，取值须与旧客户端内置常量一致；留空则拒绝匿名密文。 */
    private String anonymousKey = "";

    /** 未登录请求的 AES 初始向量，约束同 anonymousKey。 */
    private String anonymousIv = "";

    /** edu_school.area_code 为空时的兜底区划码，前端 calculateAreaLevel 对空值会直接崩。 */
    private String defaultAreaCode = "620100";

    /** edu_school 无 org_type 字段，兼容响应用此常量填充。 */
    private String orgType = "";

    /** edu_school 无 school_type 字段，兼容响应用此常量填充。 */
    private String schoolType = "";

    /** 旧协议的 isSchool：1=教育局 2=学校。Han 的教育对象都挂在学校下。 */
    private String isSchool = "2";

    /**
     * person_type 到旧 roleType 的映射，仅用于兼容目录的数据展示。
     *
     * <p>教师固定 {@code 2}，与旧前端现有的 {@code roleType == 2 || roleType == 5} 过滤天然相容。
     * 学生取值只影响名册、课程参与这类目录响应，本期不进 {@code roles[]}，
     * 因此 {@code 4} 只是沿用旧系统注释里的取值、本期不需要冻结；
     * 登录能力由 {@link #loginPersonTypes} 单独控制。
     */
    private Map<String, String> roleType = new LinkedHashMap<>(Map.of(
            "TEACHER", "2",
            "STUDENT", "4"));

    /**
     * 允许换取三课堂兼容凭证的 person_type。
     *
     * <p>本期只放行教师：旧前端的身份过滤仍是 {@code roleType == 2 || roleType == 5}，
     * 学生登录留到下一期。学生数据仍然出现在兼容目录里，只是拿不到凭证。
     */
    private Set<String> loginPersonTypes = new LinkedHashSet<>(Set.of("TEACHER"));

    /** person_type 到旧 identityName 的映射。 */
    private Map<String, String> identityName = new LinkedHashMap<>(Map.of(
            "TEACHER", "教师",
            "STUDENT", "学生"));

    /** 旧 applicationType 到 edu_device.device_type 的取值映射，未配置的取值不参与过滤。 */
    private Map<String, List<String>> deviceApplicationType = new LinkedHashMap<>();

    /** 为 true 时未配置映射的 applicationType 返回空集合，而不是不过滤。 */
    private boolean deviceApplicationTypeStrict = false;

    /** edu_class.grade_code 到年级中文名的映射，未命中时直接回落成 grade_code。 */
    private Map<String, String> gradeName = new LinkedHashMap<>();

    /** grade_code 为空的班级归入的年级名。 */
    private String ungradedName = "其他年级";

    /** 旧 dictCode 到 sys_dict_data.dict_type 的映射，未命中时按同名查询。 */
    private Map<String, String> dictType = new LinkedHashMap<>();

    /** 兼容登录页是否要求图形验证码。 */
    private boolean captchaEnabled = true;

    /** 兼容登录连续失败多少次后锁定。 */
    private int maxLoginAttempts = 5;

    /** 兼容登录锁定时长（秒）。 */
    private long loginLockSeconds = 600L;

    public String roleTypeOf(String personType) {
        String mapped = personType == null ? null : roleType.get(normalize(personType));
        return mapped != null ? mapped : "";
    }

    public String identityNameOf(String personType) {
        String mapped = personType == null ? null : identityName.get(normalize(personType));
        return mapped != null ? mapped : (personType == null ? "" : personType);
    }

    /** 该人员类型本期是否可以换取兼容凭证。目录可见性与登录能力是两件事，不要混用。 */
    public boolean canIssueToken(String personType) {
        return personType != null && loginPersonTypes.contains(normalize(personType));
    }

    private static String normalize(String personType) {
        return personType.trim().toUpperCase(Locale.ROOT);
    }
}
