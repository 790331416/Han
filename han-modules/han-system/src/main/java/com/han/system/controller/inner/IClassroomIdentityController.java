package com.han.system.controller.inner;

import com.han.api.system.domain.ClassroomIdentityVO;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.InnerAuth;
import com.han.system.sdfz.compat.LegacyClassroomIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 本地账号三课堂身份查询的内部接口，供 han-auth 从 Han 登录态换发兼容凭证。
 */
@InnerAuth
@RestController
@RequestMapping("/inner/system/external/classroom")
@RequiredArgsConstructor
public class IClassroomIdentityController {

    private final LegacyClassroomIdentityService identityService;

    @GetMapping("/identity")
    public R<ClassroomIdentityVO> identity(@RequestParam("userId") Long userId,
                                           @RequestParam(value = "identityId", required = false) String identityId) {
        return R.ok(identityService.resolve(userId, identityId));
    }

    @GetMapping("/identities")
    public R<List<ClassroomIdentityVO>> identities(@RequestParam("userId") Long userId) {
        return R.ok(identityService.list(userId));
    }

    /**
     * 数字校园按稳定外部身份 ID 精确解析本地教育身份，供 han-auth 签发数字校园登录态使用。
     *
     * <p>查询条件等价于 {@code edu_person.user_id = userId AND source_system = DIGITAL_CAMPUS
     * AND external_identity_id = externalIdentityId AND 人员有效 AND 学校有效}。
     */
    @GetMapping("/identity/by-external")
    public R<ClassroomIdentityVO> identityByExternal(@RequestParam("userId") Long userId,
                                                     @RequestParam("externalIdentityId") String externalIdentityId) {
        return R.ok(identityService.resolveByExternal(userId, externalIdentityId));
    }
}
