package com.han.system.controller.admin;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.controller.base.BSysUserController;
import com.han.system.domain.dto.SysUserDto;
import com.han.system.domain.query.SysUserQuery;
import com.han.system.domain.vo.CurrentUserVO;
import com.han.common.web.excel.ExcelUtil;
import com.han.system.domain.vo.UserExportVo;
import com.han.system.domain.vo.UserImportVo;
import com.han.system.domain.vo.UserVO;
import com.han.system.service.ISysUserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * 用户管理 - A层（管理端控制器）
 *
 * <p>面向 UI 管理系统，处理 HTTP 请求。
 */
@AdminAuth
@RestController("adminSysUserController")
@RequestMapping("/system/user")
public class ASysUserController extends BSysUserController {

    private final com.han.system.service.SysUserSocialService socialService;

    public ASysUserController(ISysUserService service, com.han.system.service.SysUserSocialService socialService) {
        super(service);
        this.socialService = socialService;
    }

    @Override
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('system:user:list')")
    public R<PageResult<UserVO>> list(SysUserQuery query) {
        return super.list(query);
    }

    @GetMapping("/client/list")
    @PreAuthorize("@ss.hasAuthority('system:client-user:list')")
    public R<PageResult<UserVO>> listClientUsers(SysUserQuery query) {
        query.setAccountType("CLIENT");
        return super.list(query);
    }

    @Override
    @GetMapping("/info/{userId}")
    @PreAuthorize("@ss.hasAuthority('system:user:query')")
    public R<SysUserDto> getInfo(@PathVariable Long userId) {
        return super.getInfo(userId);
    }

    @Override
    @RepeatSubmit
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('system:user:add')")
    @OperLog(module = "用户管理", type = OperLog.OperType.INSERT)
    public R<Void> add(@RequestBody SysUserDto dto) {
        return super.add(dto);
    }

    @Override
    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('system:user:edit')")
    @OperLog(module = "用户管理", type = OperLog.OperType.UPDATE)
    public R<Void> edit(@RequestBody SysUserDto dto) {
        return super.edit(dto);
    }

    @Override
    @RepeatSubmit
    @PostMapping("/remove/{userId}")
    @PreAuthorize("@ss.hasAuthority('system:user:remove')")
    @OperLog(module = "用户管理", type = OperLog.OperType.DELETE)
    public R<Void> remove(@PathVariable Long userId) {
        return super.remove(userId);
    }

    @RepeatSubmit
    @PostMapping("/remove")
    @PreAuthorize("@ss.hasAuthority('system:user:remove')")
    @OperLog(module = "用户管理", type = OperLog.OperType.DELETE)
    public R<Void> removeBatch(@RequestBody java.util.List<Long> userIds) {
        baseService.deleteByIds(userIds);
        return R.ok();
    }

    @Override
    @RepeatSubmit
    @PostMapping("/resetPwd")
    @PreAuthorize("@ss.hasAuthority('system:user:resetPwd')")
    @OperLog(module = "用户管理", type = OperLog.OperType.UPDATE, saveParams = false)
    public R<Void> resetPwd(@RequestParam Long userId, @RequestParam String password) {
        return super.resetPwd(userId, password);
    }

    @Override
    @RepeatSubmit
    @PostMapping("/changeStatus")
    @PreAuthorize("@ss.hasAuthority('system:user:edit')")
    @OperLog(module = "用户管理", type = OperLog.OperType.UPDATE)
    public R<Void> changeStatus(@RequestParam Long userId, @RequestParam Integer status) {
        return super.changeStatus(userId, status);
    }

    /**
     * 获取当前登录用户信息（优先从 SecurityContextHolder 获取，兼容网关 X-User-Id header）
     */
    @GetMapping("/current")
    @PermissionExempt("登录用户获取自身信息，无需特定权限")
    public R<CurrentUserVO> getCurrentUserInfo(@RequestHeader(value = "X-User-Id", required = false) String userIdStr) {
        Long userId = com.han.common.security.context.SecurityContextHolder.getUserId();
        if (userId == null && userIdStr != null && !userIdStr.isBlank()) {
            userId = Long.parseLong(userIdStr);
        }
        if (userId == null) {
            return R.fail("未获取到用户信息");
        }
        SysUserDto user = baseService.selectById(userId);
        if (user == null) {
            return R.fail("用户不存在");
        }
        Set<String> roleKeys = baseService.selectRoleKeysByUserId(userId);
        Set<String> perms = baseService.selectPermissionsByUserId(userId);
        CurrentUserVO vo = CurrentUserVO.builder()
                .userId(user.getUserId())
                .tenantId(com.han.common.security.context.SecurityContextHolder.getTenantId())
                .deptId(user.getDeptId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .email(user.getEmail())
                .roles(roleKeys != null ? roleKeys : Set.of())
                .permissions(perms != null ? perms : Set.of())
                .build();
        return R.ok(vo);
    }

    // ==================== 简单用户列表（下拉选择） ====================

    /**
     * 获取简单用户列表（用于部门负责人等下拉选择）
     * 返回当前租户下正常状态用户的 userId/nickname/phone/email
     */
    @GetMapping("/simple-list")
    @PermissionExempt("下拉选择用公共接口，无需特定权限")
    public R<java.util.List<java.util.Map<String, Object>>> simpleList() {
        var users = baseService.selectSimpleUserList();
        return R.ok(users);
    }

    // ==================== 导入导出 ====================

    @GetMapping("/export")
    @PreAuthorize("@ss.hasAuthority('system:user:export')")
    @OperLog(module = "用户管理", type = OperLog.OperType.EXPORT)
    public void export(SysUserQuery query, HttpServletResponse response) throws IOException {
        java.util.List<UserExportVo> list = baseService.selectUserPage(query).getRows().stream()
                .map(u -> UserExportVo.builder()
                        .userId(String.valueOf(u.getUserId()))
                        .username(u.getUsername())
                        .nickname(u.getNickname())
                        .deptName(u.getDeptName())
                        .phone(u.getPhone())
                        .email(u.getEmail())
                        .sexText(u.getSex() != null ? switch (u.getSex()) { case 1 -> "男"; case 2 -> "女"; default -> "未知"; } : "未知")
                        .statusText(u.getStatus() != null && u.getStatus() == 0 ? "正常" : "停用")
                        .createTime(u.getCreateTime() != null ? u.getCreateTime().toString() : "")
                        .build())
                .toList();
        ExcelUtil.exportExcel(response, "用户数据", UserExportVo.class, list);
    }

    @GetMapping("/importTemplate")
    @PreAuthorize("@ss.hasAuthority('system:user:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelUtil.exportTemplate(response, "用户导入模板", UserImportVo.class);
    }

    @RepeatSubmit(interval = 10)
    @PostMapping("/import")
    @PreAuthorize("@ss.hasAuthority('system:user:import')")
    @OperLog(module = "用户管理", type = OperLog.OperType.IMPORT)
    public R<String> importData(@RequestParam("file") MultipartFile file,
                                @RequestParam(value = "updateSupport", defaultValue = "false") boolean updateSupport) throws IOException {
        java.util.List<UserImportVo> list = ExcelUtil.importExcel(file.getInputStream(), UserImportVo.class);

        if (list == null || list.isEmpty()) {
            return R.fail("导入数据为空");
        }

        String result = baseService.importUsers(list, updateSupport);
        return R.ok(result);
    }

    // ==================== 社交绑定管理 ====================

    /**
     * 查看用户社交绑定列表（openId 脱敏返回）
     */
    @GetMapping("/{userId}/bindings")
    @PreAuthorize("@ss.hasAuthority('system:user:query')")
    public R<java.util.List<java.util.Map<String, Object>>> listBindings(@PathVariable Long userId) {
        var bindings = socialService.listByUser(userId).stream()
                .map(po -> {
                    java.util.Map<String, Object> item = new java.util.LinkedHashMap<String, Object>();
                    item.put("provider", po.getProvider());
                    item.put("nickname", po.getNickname());
                    item.put("maskedOpenId", maskOpenId(po.getOpenId()));
                    item.put("createTime", po.getCreateTime());
                    return item;
                })
                .toList();
        return R.ok(bindings);
    }

    /**
     * 强制解绑用户社交账号（管理员处置被盗号等场景，写审计日志）
     */
    @RepeatSubmit
    @PostMapping("/{userId}/unbind")
    @PreAuthorize("@ss.hasAuthority('system:user:unbind')")
    @OperLog(module = "用户管理", type = OperLog.OperType.UPDATE)
    public R<Void> forceUnbind(@PathVariable Long userId, @RequestParam String provider) {
        boolean removed = socialService.unbind(userId, provider);
        if (!removed) {
            return R.fail("该用户未绑定此第三方账号");
        }
        return R.ok();
    }

    private String maskOpenId(String openId) {
        if (openId == null || openId.isBlank()) {
            return "";
        }
        if (openId.length() <= 8) {
            return openId.charAt(0) + "***";
        }
        return openId.substring(0, 4) + "****" + openId.substring(openId.length() - 4);
    }

}
