package com.han.system.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.constant.CacheConstants;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.PermissionExempt;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.domain.po.SysLoginLogPo;
import com.han.system.domain.po.SysOperLogPo;
import com.han.system.domain.vo.DashboardStatsVO;
import com.han.system.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 首页仪表盘 - A层（管理端控制器）
 *
 * <p>根据当前用户权限动态返回统计数据，无权限的模块返回 null，前端据此隐藏对应卡片。
 */
@Slf4j
@AdminAuth
@RestController("adminSysDashboardController")
@RequestMapping("/system/dashboard")
@RequiredArgsConstructor
public class ASysDashboardController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysPostMapper postMapper;
    private final SysRoleMapper roleMapper;
    private final SysDictTypeMapper dictTypeMapper;
    private final SysNoticeMapper noticeMapper;
    private final SysOperLogMapper operLogMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final StringRedisTemplate redisTemplate;

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
                .recentLogins(has(user, "system:loginlog:list") ? recentLogins() : null)
                .recentOperLogs(has(user, "system:operlog:list") ? recentOperLogs() : null)
                .build();

        return R.ok(vo);
    }

    private boolean has(LoginUser user, String permission) {
        return user != null && user.hasPermission(permission);
    }

    private static int toInt(Long value) {
        return value != null ? value.intValue() : 0;
    }

    private int countOnlineUsers() {
        try {
            Set<String> keys = redisTemplate.keys(CacheConstants.TOKEN_KEY + "*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.warn("获取在线用户数失败", e);
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
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", po.getUsername());
            m.put("ipAddr", po.getIpAddr());
            m.put("status", po.getStatus());
            m.put("message", po.getMessage());
            m.put("loginTime", po.getLoginTime() != null ? po.getLoginTime().format(FMT) : null);
            return m;
        }).toList();
    }

    private List<Map<String, Object>> recentOperLogs() {
        List<SysOperLogPo> logs = operLogMapper.selectList(
                new LambdaQueryWrapper<SysOperLogPo>()
                        .orderByDesc(SysOperLogPo::getOperTime)
                        .last("LIMIT 5")
        );
        return logs.stream().map(po -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("module", po.getModule());
            m.put("operName", po.getOperName());
            m.put("operIp", po.getOperIp());
            m.put("status", po.getStatus());
            m.put("operTime", po.getOperTime() != null ? po.getOperTime().format(FMT) : null);
            return m;
        }).toList();
    }

    /**
     * 仪表盘图表数据（近7天登录趋势 + 操作模块分布）
     */
    @GetMapping("/charts")
    @PermissionExempt("登录用户查看图表数据")
    public R<Map<String, Object>> charts() {
        LoginUser user = SecurityContextHolder.getLoginUser();
        Map<String, Object> result = new LinkedHashMap<>();

        // 近7天登录趋势
        if (has(user, "system:loginlog:list")) {
            result.put("loginTrend", loginTrend());
        }

        // 操作模块分布 Top10
        if (has(user, "system:operlog:list")) {
            result.put("operModules", operModuleDistribution());
        }

        return R.ok(result);
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");

    private Map<String, Object> loginTrend() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(6).atStartOfDay();

        // SQL GROUP BY 聚合，避免全量加载
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
            dates.add(key);
            Map<String, Object> row = dayMap.get(key);
            successCounts.add(row != null ? ((Number) row.get("success_count")).intValue() : 0);
            failCounts.add(row != null ? ((Number) row.get("fail_count")).intValue() : 0);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dates", dates);
        m.put("success", successCounts);
        m.put("fail", failCounts);
        return m;
    }

    private List<Map<String, Object>> operModuleDistribution() {
        LocalDateTime start = LocalDate.now().minusDays(30).atStartOfDay();
        // SQL GROUP BY + ORDER BY + LIMIT，避免全量加载
        return operLogMapper.selectOperModuleDistribution(start);
    }
}
