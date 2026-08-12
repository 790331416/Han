package com.han.system.sdfz.compat;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.function.Function;

/**
 * 智慧校园契约兼容目录与兼容凭证的 HTTP 入口，共挂载 24 条旧路径。
 *
 * <p>挂载在 {@link LegacyPaths#ROOT} 下，服务两个消费者：
 * 旧 api 的 {@code CommonService}（{@code api.url} 指到这里，承担全部 15 条目录路径）
 * 与旧前端的 {@code userRequest}（nginx 把 {@code /api} 反代到这里，只剩 9 条登录与字典路径）。
 * 每条路径都同时接受 GET 与 POST，因为旧 api 用 GET 带 body、旧前端用 POST。
 *
 * <p>目录路径只挂 {@code manager/*} 一套：2026-08-12 旧侧把前端的目录调用切回了
 * 旧 api 的 {@code /common/*}，带 {@code user/} 前缀的 4 条重复路径已无调用方。
 *
 * <p>不是 Han 管理端接口，不参与 {@code @AdminAuth} 的权限校验：
 * 调用方是旧系统而非 Han 前端，身份由兼容凭证与 Han Gateway 承担。
 */
@RestController
@RequestMapping(LegacyPaths.ROOT)
@RequiredArgsConstructor
public class LegacyCompatController {

    private final LegacyCompatSupport support;
    private final LegacyDirectoryService directory;
    private final LegacyCredentialService credentials;

    // ------------------------------------------------------------ 通道 B：旧 api CommonService

    @RequestMapping(value = LegacyPaths.USER_INFO_GET_BY_ID, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> userInfoById(HttpServletRequest request) {
        return handle(request, LegacyPaths.USER_INFO_GET_BY_ID, directory::userInfo);
    }

    @RequestMapping(value = LegacyPaths.USER_INFO_GET_USER_INFO, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> userInfoByPhone(HttpServletRequest request) {
        return handle(request, LegacyPaths.USER_INFO_GET_USER_INFO, directory::userInfo);
    }

    @RequestMapping(value = LegacyPaths.IDENTITY_GET_BY_PK_ID, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> identity(HttpServletRequest request) {
        return handle(request, LegacyPaths.IDENTITY_GET_BY_PK_ID, directory::identity);
    }

    @RequestMapping(value = LegacyPaths.ORG_CHILD_LIST, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> orgChildren(HttpServletRequest request) {
        return handle(request, LegacyPaths.ORG_CHILD_LIST, directory::orgChildren);
    }

    @RequestMapping(value = LegacyPaths.ORG_GET_BY_ID, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> orgById(HttpServletRequest request) {
        return handle(request, LegacyPaths.ORG_GET_BY_ID, directory::org);
    }

    @RequestMapping(value = LegacyPaths.ORG_LIST_BY_PAGE, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> orgPage(HttpServletRequest request) {
        return handle(request, LegacyPaths.ORG_LIST_BY_PAGE, directory::orgPage);
    }

    @RequestMapping(value = LegacyPaths.ORG_SCHOOL_INFO, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> schoolInfo(HttpServletRequest request) {
        return handle(request, LegacyPaths.ORG_SCHOOL_INFO, directory::schoolPage);
    }

    @RequestMapping(value = LegacyPaths.MANAGER_ORG_INFO_FOR_EXTERNAL, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> managerOrgForExternal(HttpServletRequest request) {
        return handle(request, LegacyPaths.MANAGER_ORG_INFO_FOR_EXTERNAL, directory::org);
    }

    @RequestMapping(value = LegacyPaths.MANAGER_LAZY_ORG_TREE, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> managerLazyOrgTree(HttpServletRequest request) {
        return handle(request, LegacyPaths.MANAGER_LAZY_ORG_TREE, directory::lazyOrgTree);
    }

    @RequestMapping(value = LegacyPaths.MANAGER_ORG_BRANCH_TREE, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> managerOrgBranchTree(HttpServletRequest request) {
        return handle(request, LegacyPaths.MANAGER_ORG_BRANCH_TREE, directory::orgBranchTree);
    }

    @RequestMapping(value = LegacyPaths.PINYIN_ORG_RESULT, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> orgResultByAreaCode(HttpServletRequest request) {
        return handle(request, LegacyPaths.PINYIN_ORG_RESULT, directory::orgSearch);
    }

    @RequestMapping(value = LegacyPaths.MANAGER_TEACHER_LIST, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> managerTeachers(HttpServletRequest request) {
        return handle(request, LegacyPaths.MANAGER_TEACHER_LIST, directory::teachers);
    }

    @RequestMapping(value = LegacyPaths.SELECT_PLACE, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> selectPlace(HttpServletRequest request) {
        return handle(request, LegacyPaths.SELECT_PLACE, directory::places);
    }

    @RequestMapping(value = LegacyPaths.DEVICE_LIST, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> deviceList(HttpServletRequest request) {
        return handle(request, LegacyPaths.DEVICE_LIST, directory::devices);
    }

    @RequestMapping(value = LegacyPaths.DEVICE_BY_CODE, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> deviceByCode(HttpServletRequest request) {
        return handle(request, LegacyPaths.DEVICE_BY_CODE, directory::device);
    }

    // ------------------------------------------------------------ 通道 C：旧前端 userRequest

    @RequestMapping(value = LegacyPaths.UI_RANDOM_IMAGE + "/{checkKey}", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> randomImage(HttpServletRequest request, @PathVariable String checkKey) {
        return handle(request, LegacyPaths.UI_RANDOM_IMAGE,
                ignored -> credentials.captcha(checkKey));
    }

    @RequestMapping(value = LegacyPaths.UI_LOGIN, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> login(HttpServletRequest request) {
        return handle(request, LegacyPaths.UI_LOGIN, credentials::login);
    }

    @RequestMapping(value = LegacyPaths.UI_GET_ONE_BY_ID, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> currentUser(HttpServletRequest request) {
        return handle(request, LegacyPaths.UI_GET_ONE_BY_ID, credentials::currentUser);
    }

    @RequestMapping(value = LegacyPaths.UI_DICT_ITEMS + "/{dictCode}", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> dictItems(HttpServletRequest request, @PathVariable String dictCode) {
        return handle(request, LegacyPaths.UI_DICT_ITEMS, ignored -> directory.dictItems(dictCode));
    }

    // ------------------------------------------------------------ 本期未启用的通道 C 接口

    @RequestMapping(value = LegacyPaths.UI_LOGIN_BY_CAPTCHA, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> loginByCaptcha(HttpServletRequest request) {
        return support.unavailable(request, LegacyPaths.UI_LOGIN_BY_CAPTCHA, "短信验证码登录本期未启用");
    }

    @RequestMapping(value = LegacyPaths.UI_SMS_CODE, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> smsCode(HttpServletRequest request) {
        return support.unavailable(request, LegacyPaths.UI_SMS_CODE, "短信验证码本期未启用");
    }

    @RequestMapping(value = LegacyPaths.UI_FORGET_PASSWORD, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> forgetPassword(HttpServletRequest request) {
        return support.unavailable(request, LegacyPaths.UI_FORGET_PASSWORD, "忘记密码本期未启用，请联系管理员重置");
    }

    @RequestMapping(value = LegacyPaths.UI_JYY_SSO, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> jyySso(HttpServletRequest request) {
        return support.unavailable(request, LegacyPaths.UI_JYY_SSO, "教育云单点登录属于数字校园通路，本期已冻结");
    }

    @RequestMapping(value = LegacyPaths.UI_FILE_VIEW_AUTH, method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> fileViewAuth(HttpServletRequest request) {
        return support.unavailable(request, LegacyPaths.UI_FILE_VIEW_AUTH, "文件预览授权本期未启用");
    }

    private Map<String, Object> handle(HttpServletRequest request, String path,
                                       Function<LegacyRequest, LegacyPayload> handler) {
        return support.handle(request, path, handler);
    }
}
