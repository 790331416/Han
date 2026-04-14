package com.han.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.system.domain.po.SysOperLogPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 操作日志 Mapper 接口
 */
@Mapper
public interface SysOperLogMapper extends BaseMapper<SysOperLogPo> {

    @Select("""
            SELECT title AS module,
                   oper_name,
                   oper_ip,
                   status,
                   oper_time
              FROM sys_oper_log
             WHERE oper_time IS NOT NULL
             ORDER BY oper_time DESC
             LIMIT 5
            """)
    List<Map<String, Object>> selectRecentOperLogs();

    /**
     * 按模块统计操作次数 Top10（仪表盘图表用）
     */
    @Select("""
            SELECT title AS name, COUNT(*) AS value
              FROM sys_oper_log
             WHERE oper_time >= #{start} AND title IS NOT NULL AND title != ''
             GROUP BY title
             ORDER BY value DESC
             LIMIT 10
            """)
    List<Map<String, Object>> selectOperModuleDistribution(@Param("start") LocalDateTime start);
}
