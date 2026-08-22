package com.han.open.controller;

import com.han.common.core.domain.R;
import com.han.common.core.domain.PageResult;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.open.domain.vo.AppCredentialVO;
import com.han.open.domain.vo.AppGrantDetailVO;
import com.han.open.domain.vo.GrantApplyVO;
import com.han.open.domain.vo.OpenAppCredentialAdminVO;
import com.han.open.domain.vo.OpenAuthorizationRequestAdminVO;
import com.han.open.service.OpenAppAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 应用授权控制器
 */
@AdminAuth
@Tag(name = "应用授权管理", description = "授权申请、审核、凭证管理、权限校验接口")
@RestController
@RequestMapping("/open/authorization")
@RequiredArgsConstructor
public class OpenAppAuthorizationController {

    private final OpenAppAuthorizationService authorizationService;

    @Operation(summary = "分页查询授权申请")
    @GetMapping({"/requests", "/request/list"})
    @PreAuthorize("@ss.hasAuthority('open:grant:query')")
    public R<PageResult<OpenAuthorizationRequestAdminVO>> requests(
            @RequestParam(required = false) Long appId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(authorizationService.listRequestPage(appId, status, environment, pageNum, pageSize));
    }

    @Operation(summary = "查询应用分环境凭证")
    @GetMapping({"/credentials", "/credential/list"})
    @PreAuthorize("@ss.hasAuthority('open:credential:query')")
    public R<List<OpenAppCredentialAdminVO>> credentials(@RequestParam(required = false) Long appId) {
        return R.ok(authorizationService.listCredentials(appId));
    }

    @Operation(summary = "提交授权申请")
    @PostMapping("/apply")
    @PreAuthorize("@ss.hasAuthority('open:grant:apply')")
    @RepeatSubmit
    @OperLog(module = "应用授权", type = OperLog.OperType.GRANT)
    public R<Long> submitGrantApply(@Validated @RequestBody GrantApplyVO applyVO) {
        return R.ok(authorizationService.submitGrantApply(applyVO));
    }

    @Operation(summary = "审核授权申请")
    @RequestMapping(value = "/review/{requestId}", method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("@ss.hasAuthority('open:grant:review')")
    @RepeatSubmit
    @OperLog(module = "应用授权", type = OperLog.OperType.GRANT)
    public R<Void> reviewGrantApply(@PathVariable Long requestId,
                                     @RequestParam Integer status,
                                     @RequestParam(required = false) String reason) {
        authorizationService.reviewGrantApply(requestId, status, reason);
        return R.ok();
    }

    @Operation(summary = "获取应用授权列表")
    @GetMapping("/app/{appId}")
    @PreAuthorize("@ss.hasAuthority('open:grant:query')")
    public R<List<AppGrantDetailVO>> listAppGrants(@PathVariable Long appId) {
        return R.ok(authorizationService.listAppGrants(appId));
    }

    @Operation(summary = "撤销应用授权")
    @RequestMapping(value = "/revoke/{grantId}", method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("@ss.hasAuthority('open:grant:revoke')")
    @RepeatSubmit
    @OperLog(module = "应用授权", type = OperLog.OperType.GRANT)
    public R<Void> revokeGrant(@PathVariable Long grantId,
                               @RequestParam(required = false) String reason) {
        authorizationService.revokeGrant(grantId, reason);
        return R.ok();
    }

    @Operation(summary = "生成应用凭证")
    @PostMapping("/credential/generate")
    @PreAuthorize("@ss.hasAuthority('open:credential:manage')")
    @RepeatSubmit
    @OperLog(module = "应用凭证", type = OperLog.OperType.INSERT, saveParams = false, saveResult = false)
    public R<AppCredentialVO> generateCredential(@RequestParam Long appId,
                                                  @RequestParam String environment) {
        return R.ok(authorizationService.generateCredential(appId, environment));
    }

    @Operation(summary = "轮换应用凭证")
    @RequestMapping(value = "/credential/rotate/{credentialId}", method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("@ss.hasAuthority('open:credential:manage')")
    @RepeatSubmit
    @OperLog(module = "应用凭证", type = OperLog.OperType.UPDATE, saveParams = false, saveResult = false)
    public R<AppCredentialVO> rotateCredential(@PathVariable Long credentialId) {
        return R.ok(authorizationService.rotateCredential(credentialId));
    }

}
