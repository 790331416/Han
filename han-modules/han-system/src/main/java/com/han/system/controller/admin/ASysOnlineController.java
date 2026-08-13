package com.han.system.controller.admin;

import com.han.common.core.domain.R;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.system.domain.vo.OnlineUserVo;
import com.han.system.service.SysOnlineSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 在线用户管理 - A层（管理端控制器）
 */
@Slf4j
@AdminAuth
@RestController("adminSysOnlineController")
@RequestMapping("/system/online")
@RequiredArgsConstructor
public class ASysOnlineController {

    private final SysOnlineSessionService onlineSessionService;

    /**
     * 在线会话列表。
     *
     * <p>只返回当前租户的会话（超管看全平台），扫描走 SCAN 而不是阻塞式 KEYS。
     * Redis 异常不再吞掉——把故障伪装成「无人在线」比直接报错更危险。
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('monitor:online:list')")
    public R<List<OnlineUserVo>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String ipAddr) {
        List<OnlineUserVo> list = onlineSessionService.listVisibleSessions(username, ipAddr).stream()
                .map(s -> OnlineUserVo.builder()
                        .tokenId(s.tokenId())
                        .userId(s.userId())
                        .username(s.username())
                        .nickname(s.nickname())
                        .ipAddr(s.ipAddr())
                        .clientType(s.clientType())
                        .loginTime(s.loginTime())
                        .build())
                .toList();
        return R.ok(list);
    }

    @PostMapping("/forceLogout")
    @PreAuthorize("@ss.hasAuthority('monitor:online:forceLogout')")
    @OperLog(module = "在线用户", type = OperLog.OperType.FORCE_LOGOUT)
    public R<Void> forceLogout(@RequestBody Map<String, String> body) {
        if (!onlineSessionService.forceLogout(body.get("tokenId"))) {
            return R.fail("该会话已不存在或已过期");
        }
        return R.ok();
    }
}
