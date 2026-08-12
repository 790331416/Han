package com.han.system.sdfz.compat;

/**
 * 旧三课堂两条通道的接口路径常量，共 24 个。
 *
 * <p>取值即旧 api {@code CommonService} 的路径常量与旧前端 {@code userRequest} 的相对路径，
 * 也是 {@code x-platform} 解密后的内容，切勿"修正"其中的既有拼写缺陷。
 *
 * <p>2026-08-12 旧侧把前端的 7 个目录调用切回了旧 api 的 {@code /common/*}，
 * 原来带 {@code user/} 前缀的 4 个重复目录路径（懒加载组织树、对外组织信息、年级班级树、教师列表）
 * 已不再有调用方，这里不再挂载：目录流量统一走 {@code manager/*} 一套。
 */
public final class LegacyPaths {

    /** 兼容层挂载前缀，旧 api 的 {@code api.url} 与 nginx {@code /api} 反代都指到这里。 */
    public static final String ROOT = "/sdfz-compat";

    // 通道 B：旧 api CommonService 直连 HTTP
    public static final String USER_INFO_GET_BY_ID = "user/userInfo/getById";
    public static final String USER_INFO_GET_USER_INFO = "user/userInfo/getUserInfo";
    public static final String IDENTITY_GET_BY_PK_ID = "user/identity/getIdentityBypkId";
    public static final String ORG_CHILD_LIST = "user/org/getOrgChildList";
    public static final String ORG_GET_BY_ID = "user/org/getById";
    public static final String ORG_LIST_BY_PAGE = "user/org/org-list-by-page";
    public static final String ORG_SCHOOL_INFO = "user/org/getSchoolInfo";
    public static final String MANAGER_ORG_INFO_FOR_EXTERNAL = "manager/org/getOrgInfoForExternal";
    public static final String MANAGER_LAZY_ORG_TREE = "manager/org/get-lazy-org-tree";
    public static final String MANAGER_ORG_BRANCH_TREE = "manager/org-branch/get-org-branch-tree";
    public static final String PINYIN_ORG_RESULT = "manager/pinyin/get-org-result-by-areaCode";
    public static final String MANAGER_TEACHER_LIST = "manager/teacher/getTeacherInfoList";
    public static final String SELECT_PLACE = "configuration/school/place/selectPlace";
    public static final String DEVICE_LIST = "device/sysDevice/getDeviceList";
    public static final String DEVICE_BY_CODE = "device/sysDevice/getDeviceInfoByDeviceCode";

    // 通道 C：旧前端 userRequest 直连 /api，2026-08-12 收敛后只剩登录、当前用户、验证码、字典、文件预览授权
    public static final String UI_RANDOM_IMAGE = "user/sys/randomImage";
    public static final String UI_LOGIN = "user/user/login";
    public static final String UI_GET_ONE_BY_ID = "user/user/getOneById";
    public static final String UI_LOGIN_BY_CAPTCHA = "user/user/loginByCaptcha";
    public static final String UI_SMS_CODE = "user/public/login/get-sms-code";
    public static final String UI_FORGET_PASSWORD = "user/user/user-forget-password";
    public static final String UI_JYY_SSO = "partner/tPartnerUserLogin/userVoByJyyToken";
    public static final String UI_DICT_ITEMS = "user/sys/dict/getDictItems";
    public static final String UI_FILE_VIEW_AUTH = "sidecar/fileview/authorizationCode";

    private LegacyPaths() {
    }
}
