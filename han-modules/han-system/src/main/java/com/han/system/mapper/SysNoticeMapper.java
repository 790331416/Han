package com.han.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.system.domain.po.SysNoticePo;
import com.han.system.domain.vo.NoticeLatestVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysNoticeMapper extends BaseMapper<SysNoticePo> {

    /**
     * 查询当前用户最新通知及其已读状态。
     *
     * @param userId 用户 ID
     * @param limit 返回条数
     * @return 通知列表
     */
    @Results({
            @Result(column = "read_flag", property = "read"),
            @Result(column = "read_time", property = "readTime")
    })
    @Select("""
            <script>
            SELECT
                n.id,
                n.tenant_id,
                n.notice_title,
                n.notice_type,
                n.notice_content,
                n.status,
                n.create_by,
                n.create_name,
                n.update_by,
                n.update_name,
                n.create_dept,
                n.create_time,
                n.update_time,
                n.del_flag,
                n.remark,
                CASE WHEN r.id IS NULL THEN FALSE ELSE TRUE END AS read_flag,
                r.read_time AS read_time
            FROM sys_notice n
            LEFT JOIN sys_notice_read r
                ON r.notice_id = n.id
               AND r.user_id = #{userId}
               AND r.del_flag = 0
            WHERE n.status = 0
              AND n.del_flag = 0
            ORDER BY n.create_time DESC
            LIMIT #{limit}
            </script>
            """)
    List<NoticeLatestVo> selectLatestForUser(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 统计当前用户未读通知数。
     *
     * @param userId 用户 ID
     * @return 未读通知数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM sys_notice n
            WHERE n.status = 0
              AND n.del_flag = 0
              AND NOT EXISTS (
                  SELECT 1
                  FROM sys_notice_read r
                  WHERE r.notice_id = n.id
                    AND r.user_id = #{userId}
                    AND r.del_flag = 0
              )
            </script>
            """)
    Long countUnreadForUser(@Param("userId") Long userId);
}
