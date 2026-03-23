package com.han.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.system.domain.po.SysLoginLogPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 登录日志 Mapper 接口
 */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLogPo> {

    /**
     * 按天统计登录成功/失败次数（仪表盘图表用）
     */
    @Select("""
            SELECT TO_CHAR(login_time, 'MM-DD') AS day,
                   SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS success_count,
                   SUM(CASE WHEN status != 0 THEN 1 ELSE 0 END) AS fail_count
              FROM sys_login_log
             WHERE login_time >= #{start}
             GROUP BY TO_CHAR(login_time, 'MM-DD')
             ORDER BY day
            """)
    List<Map<String, Object>> selectLoginTrend(@Param("start") LocalDateTime start);
}
