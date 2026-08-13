package com.han.system.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.domain.po.SysLoginLogPo;
import com.han.system.domain.vo.DashboardStatsVO;
import com.han.system.mapper.SysDeptMapper;
import com.han.system.mapper.SysDictTypeMapper;
import com.han.system.mapper.SysLoginLogMapper;
import com.han.system.mapper.SysNoticeMapper;
import com.han.system.mapper.SysOperLogMapper;
import com.han.system.mapper.SysPostMapper;
import com.han.system.mapper.SysRoleMapper;
import com.han.system.mapper.SysUserMapper;
import com.han.system.service.SysOnlineSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端首页仪表盘控制器。
 */
@Slf4j
@AdminAuth
@RestController("adminSysDashboardController")
@RequestMapping("/system/dashboard")
@RequiredArgsConstructor
public class ASysDashboardController {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysPostMapper postMapper;
    private final SysRoleMapper roleMapper;
    private final SysDictTypeMapper dictTypeMapper;
    private final SysNoticeMapper noticeMapper;
    private final SysOperLogMapper operLogMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final SysOnlineSessionService onlineSessionService;

    @GetMapping("/stats")
    @PermissionExempt("登录用户查看首页统计，各模块数据按权限过滤")
    public R<DashboardStatsVO> stats() {
        LoginUser user = SecurityContextHolder.getLoginUser();

        DashboardStatsVO vo = DashboardStatsVO.builder()
                .userCount(has(user, "system:user:list") ? toInt(userMapper.selectCount(null)) : null)
                .roleCount(has(user, "system:role:list") ? toInt(roleMapper.selectCount(null)) : null)
                .deptCount(has(user, "system:dept:list") ? toInt(deptMapper.selectCount(null)) : null)
                .postCount(has(user, "system:post:list") ? toInt(postMapper.selectCount(null)) : null)
                .onlineCount(has(user, "monitor:online:list") ? countOnlineUsers() : null)
                .dictCount(has(user, "system:dict:list") ? toInt(dictTypeMapper.selectCount(null)) : null)
                .noticeCount(has(user, "system:notice:list") ? toInt(noticeMapper.selectCount(null)) : null)
                .recentLogins(has(user, "monitor:loginlog:list") ? recentLogins() : null)
                .recentOperLogs(has(user, "monitor:operlog:list") ? recentOperLogs() : null)
                .springBootVersion(org.springframework.boot.SpringBootVersion.getVersion())
                .build();

        return R.ok(vo);
    }

    @GetMapping("/charts")
    @PermissionExempt("登录用户查看图表数据")
    public R<Map<String, Object>> charts() {
        LoginUser user = SecurityContextHolder.getLoginUser();
        Map<String, Object> result = new LinkedHashMap<>();

        // 权限键以 sql/tiers 种子为准：monitor:loginlog / monitor:operlog，
        // 此前写的 system:* 前缀在种子里不存在，两块面板对非超管永远是空的
        if (has(user, "monitor:loginlog:list")) {
            result.put("loginTrend", loginTrend());
        }
        if (has(user, "monitor:operlog:list")) {
            result.put("operModules", operModuleDistribution());
        }

        return R.ok(result);
    }

    private boolean has(LoginUser user, String permission) {
        return user != null && user.hasPermission(permission);
    }

    private static int toInt(Long value) {
        return value != null ? value.intValue() : 0;
    }

    private int countOnlineUsers() {
        try {
            return onlineSessionService.countVisibleSessions();
        } catch (Exception e) {
            log.warn("Failed to count online users", e);
            return 0;
        }
    }

    private List<Map<String, Object>> recentLogins() {
        List<SysLoginLogPo> logs = loginLogMapper.selectList(
                new LambdaQueryWrapper<SysLoginLogPo>()
                        .orderByDesc(SysLoginLogPo::getLoginTime)
                        .last("LIMIT 5")
        );
        return logs.stream().map(po -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("username", po.getUsername());
            item.put("ipAddr", po.getIpAddr());
            item.put("status", po.getStatus());
            item.put("message", po.getMessage());
            item.put("loginTime", formatDateTime(po.getLoginTime()));
            return item;
        }).toList();
    }

    private List<Map<String, Object>> recentOperLogs() {
        List<Map<String, Object>> logs = operLogMapper.selectRecentOperLogs();
        return logs.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("module", row.get("module"));
            item.put("operName", row.get("oper_name"));
            item.put("operIp", row.get("oper_ip"));
            item.put("status", row.get("status"));
            item.put("operTime", formatDateTime(row.get("oper_time")));
            return item;
        }).toList();
    }

    private Map<String, Object> loginTrend() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(6).atStartOfDay();

        List<Map<String, Object>> rows = loginLogMapper.selectLoginTrend(start);
        Map<String, Map<String, Object>> dayMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            dayMap.put(String.valueOf(row.get("day")), row);
        }

        List<String> dates = new ArrayList<>();
        List<Integer> successCounts = new ArrayList<>();
        List<Integer> failCounts = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String key = day.format(DATE_FMT);
            Map<String, Object> row = dayMap.get(key);
            dates.add(key);
            successCounts.add(row != null ? ((Number) row.get("success_count")).intValue() : 0);
            failCounts.add(row != null ? ((Number) row.get("fail_count")).intValue() : 0);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("success", successCounts);
        result.put("fail", failCounts);
        return result;
    }

    private List<Map<String, Object>> operModuleDistribution() {
        return operLogMapper.selectOperModuleDistribution(LocalDate.now().minusDays(30).atStartOfDay());
    }

    private String formatDateTime(Object value) {
        if (value instanceof LocalDateTime time) {
            return time.format(DATE_TIME_FMT);
        }
        return value != null ? String.valueOf(value) : null;
    }
}
