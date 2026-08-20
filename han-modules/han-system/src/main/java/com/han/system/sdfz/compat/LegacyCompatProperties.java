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
     * <b>身份类型</b>：person_type 到旧 roleType 的映射。
     *
     * <p>教师固定 {@code 2}，与旧前端现有的 {@code roleType == 2 || roleType == 5} 过滤天然相容——
     * 这个过滤在 {@code store/user.ts} 的登录链路上，取值改成别的教师<b>直接登不进去</b>。
     * 学生取值只影响名册、课程参与这类目录响应，本期不进 {@code roles[]}，
     * 因此 {@code 4} 只是沿用旧系统注释里的取值、本期不需要冻结；
     * 登录能力由 {@link #loginPersonTypes} 单独控制。
     *
     * <p><b>这一项只喂身份维度。</b>控制台菜单看的是岗位维度，见 {@link #dutyType}——
     * 旧前端把两者都叫 {@code roleType}，但它们是两个不同的编码空间，一个值不能同时喂两边。
     */
    private Map<String, String> roleType = new LinkedHashMap<>(Map.of(
            "TEACHER", "2",
            "STUDENT", "4"));

    /**
     * <b>岗位</b>：{@code edu_person.duty_code} 到旧岗位码的映射，喂 {@code roles[].dutyType[].roleType}。
     *
     * <p>旧前端的控制台菜单按 {@code isSchool + '-' + dutyType[].roleType} 匹配 {@code meta.role}
     * （见 {@code consolr-menu/index.vue}），路由表注释给出的编码空间是：
     * 教师 3/4/5、管理员 1/9、教育局管理员 1/2/5/9。
     * 因此普通教师取 {@code 3}（拼出 {@code 2-3}，不命中任何校级菜单），
     * 管理员取 {@code 1}（拼出 {@code 2-1}，命中课程预约、授课统计、学校设置、学校直播间、学校结对）。
     *
     * <p>没选 {@code 1} 之外的 {@code 9}：{@code 2-9} 会额外让前端把课程列表切成"全校口径"
     * （{@code roleType.includes('2-9')} 分支走 {@code getCourseInfoList}），
     * 那是一次数据可见范围的扩大，与"打通建课入口"是两件事，需要单独决策。
     */
    private Map<String, String> dutyType = new LinkedHashMap<>(Map.of(
            "TEACHER", "3",
            "SCHOOL_ADMIN", "1"));

    /**
     * 岗位到岗位名称的映射，喂 {@code dutyType[].positionName} 与 {@code itemText}（回看设置页会直接显示它）。
     */
    private Map<String, String> dutyName = new LinkedHashMap<>(Map.of(
            "TEACHER", "普通教师",
            "SCHOOL_ADMIN", "管理员"));

    /**
     * {@code duty_code} 为空时的兜底岗位。
     *
     * <p><b>必须是普通教师。</b>存量人员在引入岗位维度之前没有这个字段，
     * 兜底成管理岗等于给全校教师默认发校级管理权限——那不是补映射，是放权。
     */
    private String defaultDuty = "TEACHER";

    /**
     * 允许换取三课堂兼容凭证的 person_type。
     *
     * <p>默认放行教师和学生。旧前端仍只渲染教师角色，学生应接入新的 H5/App；
     * 学生可访问的校端路径由管理端网关和校端服务共同限制。
     */
    private Set<String> loginPersonTypes = new LinkedHashSet<>(Set.of("TEACHER", "STUDENT"));

    /** person_type 到旧 identityName 的映射。 */
    private Map<String, String> identityName = new LinkedHashMap<>(Map.of(
            "TEACHER", "教师",
            "STUDENT", "学生"));

    /** 旧 applicationType 到 edu_device.device_type 的取值映射，未配置的取值不参与过滤。 */
    private Map<String, List<String>> deviceApplicationType = new LinkedHashMap<>();

    /** 为 true 时未配置映射的 applicationType 返回空集合，而不是不过滤。 */
    private boolean deviceApplicationTypeStrict = false;

    /** edu_class.grade_code 到年级中文名的映射；默认覆盖教育基础数据中的标准年级编码。 */
    private Map<String, String> gradeName = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("G001", "小班"),
            Map.entry("G002", "中班"),
            Map.entry("G003", "大班"),
            Map.entry("G004", "一年级"),
            Map.entry("G005", "二年级"),
            Map.entry("G006", "三年级"),
            Map.entry("G007", "四年级"),
            Map.entry("G008", "五年级"),
            Map.entry("G009", "六年级"),
            Map.entry("G010", "七年级"),
            Map.entry("G011", "八年级"),
            Map.entry("G012", "九年级"),
            Map.entry("G013", "高一年级"),
            Map.entry("G014", "高二年级"),
            Map.entry("G015", "高三年级"),
            Map.entry("G920", "学前班"),
            Map.entry("G930", "毕业年级"),
            Map.entry("G940", "其他年级")));

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

    /**
     * 岗位码。
     *
     * <p>取不到映射时回落到默认岗位（普通教师）而不是空串：空串会拼出 {@code 2-}，
     * 既匹配不上任何菜单，也让排查时看不出是"没配岗位"还是"配错了岗位"。
     */
    public String dutyCodeOf(String duty) {
        String mapped = duty == null ? null : dutyType.get(normalize(duty));
        if (mapped != null) {
            return mapped;
        }
        String fallback = defaultDuty == null ? null : dutyType.get(normalize(defaultDuty));
        return fallback != null ? fallback : "";
    }

    public String dutyNameOf(String duty) {
        String mapped = duty == null ? null : dutyName.get(normalize(duty));
        if (mapped != null) {
            return mapped;
        }
        String fallback = defaultDuty == null ? null : dutyName.get(normalize(defaultDuty));
        return fallback != null ? fallback : "";
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
