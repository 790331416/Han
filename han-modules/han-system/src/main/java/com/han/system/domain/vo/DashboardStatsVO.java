package com.han.system.domain.vo;

import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 首页仪表盘统计数据（只读值对象）
 *
 * <p>所有 count 字段为 Integer 包装类型，null 表示当前用户无该模块权限，前端据此隐藏对应卡片。
 * <p>使用 Integer 而非 Long，避免全局 Jackson Long→String 序列化器将 count 值转为字符串。
 */
@Builder
public record DashboardStatsVO(
        /** 用户总数（需 system:user:list） */
        Integer userCount,
        /** 角色数量（需 system:role:list） */
        Integer roleCount,
        /** 部门数量（需 system:dept:list） */
        Integer deptCount,
        /** 岗位数量（需 system:post:list） */
        Integer postCount,
        /** 在线用户（需 monitor:online:list） */
        Integer onlineCount,
        /** 字典类型数（需 system:dict:list） */
        Integer dictCount,
        /** 通知公告数（需 system:notice:list） */
        Integer noticeCount,
        /** 定时任务数（需 job:list，跨服务暂不统计） */
        Integer jobCount,
        /** 最近5条登录日志（需 system:loginlog:list） */
        List<Map<String, Object>> recentLogins,
        /** 最近5条操作日志（需 system:operlog:list） */
        List<Map<String, Object>> recentOperLogs
) {
}
