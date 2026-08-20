package com.han.system.sdfz.education.domain;

import java.util.Locale;

/**
 * 教育人员的校内岗位。
 *
 * <p>与 {@code person_type}（身份类型：教师 / 学生）是<b>两个维度</b>，不要混用：
 * 身份类型回答"这个人是什么人"，岗位回答"这个人在学校里担任什么职务"。
 * 一名教师默认只是普通教师；管理员必须由管理员在人员表单里显式授予。
 *
 * <p>旧三课堂前端的控制台菜单按岗位授权（{@code isSchool + '-' + dutyType[].roleType}），
 * 岗位到旧岗位码的映射是对端契约，放在 {@code sdfz.compat.duty-type} 配置里，不写死在这里。
 */
public enum EduDuty {

    /** 普通教师：只能进入面向本人的页面（我的课程、课程回看、我的直播间等）。 */
    TEACHER("普通教师"),

    /** 管理员：额外可进入课程预约、授课统计、学校设置、学校直播间等校级页面。 */
    SCHOOL_ADMIN("管理员");

    private final String label;

    EduDuty(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 解析岗位标识，空白或无法识别时返回 {@code null}，由调用方决定回落还是报错。 */
    public static EduDuty of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
