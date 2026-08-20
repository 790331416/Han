package com.han.system.sdfz.education;

/** 教育管理新增接口权限，避免在控制器中散落相同字符串。 */
public final class EducationPermissions {

    private EducationPermissions() {
    }

    public static final String ACADEMIC_YEAR_LIST = "education:academic-year:list";
    public static final String ACADEMIC_YEAR_ADD = "education:academic-year:add";
    public static final String ACADEMIC_YEAR_EDIT = "education:academic-year:edit";
    public static final String ACADEMIC_YEAR_REMOVE = "education:academic-year:remove";
    public static final String SCOPE_MANAGE = "education:scope:manage";
    public static final String REGION_MANAGE = "education:region:manage";
    public static final String REGION_REMOVE = "education:region:remove";
    public static final String PROMOTION_LIST = "education:promotion:list";
    public static final String PROMOTION_PREVIEW = "education:promotion:preview";
    public static final String PROMOTION_CONFIRM = "education:promotion:confirm";
    public static final String COURSE_RULE_LIST = "education:course-rule:list";
    public static final String COURSE_RULE_ADD = "education:course-rule:add";
    public static final String COURSE_RULE_EDIT = "education:course-rule:edit";
    public static final String COURSE_RULE_REMOVE = "education:course-rule:remove";

    public static final String HAS_ACADEMIC_YEAR_LIST = "@ss.hasAuthority('" + ACADEMIC_YEAR_LIST + "')";
    public static final String HAS_ACADEMIC_YEAR_ADD = "@ss.hasAuthority('" + ACADEMIC_YEAR_ADD + "')";
    public static final String HAS_ACADEMIC_YEAR_EDIT = "@ss.hasAuthority('" + ACADEMIC_YEAR_EDIT + "')";
    public static final String HAS_ACADEMIC_YEAR_REMOVE = "@ss.hasAuthority('" + ACADEMIC_YEAR_REMOVE + "')";
    public static final String HAS_SCOPE_MANAGE = "@ss.hasAuthority('" + SCOPE_MANAGE + "')";
    public static final String HAS_REGION_MANAGE = "@ss.hasAuthority('" + REGION_MANAGE + "')";
    public static final String HAS_REGION_REMOVE = "@ss.hasAuthority('" + REGION_REMOVE + "')";
    public static final String HAS_PROMOTION_LIST = "@ss.hasAuthority('" + PROMOTION_LIST + "')";
    public static final String HAS_PROMOTION_PREVIEW = "@ss.hasAuthority('" + PROMOTION_PREVIEW + "')";
    public static final String HAS_PROMOTION_CONFIRM = "@ss.hasAuthority('" + PROMOTION_CONFIRM + "')";
    public static final String HAS_COURSE_RULE_LIST = "@ss.hasAuthority('" + COURSE_RULE_LIST + "')";
    public static final String HAS_COURSE_RULE_ADD = "@ss.hasAuthority('" + COURSE_RULE_ADD + "')";
    public static final String HAS_COURSE_RULE_EDIT = "@ss.hasAuthority('" + COURSE_RULE_EDIT + "')";
    public static final String HAS_COURSE_RULE_REMOVE = "@ss.hasAuthority('" + COURSE_RULE_REMOVE + "')";
}
