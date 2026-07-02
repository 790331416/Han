package com.han.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.system.domain.po.SysDeptPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysDeptMapper extends BaseMapper<SysDeptPo> {

    /**
     * 查询部门列表（LEFT JOIN sys_user 取负责人姓名）
     */
    @Select("""
            <script>
            SELECT d.*, u.nickname AS leader_name
            FROM sys_dept d
            LEFT JOIN sys_user u ON d.leader_id = u.id AND u.del_flag = 0
            WHERE d.del_flag = 0
            <if test="deptName != null and deptName != ''">
                AND d.dept_name LIKE '%' || #{deptName} || '%'
            </if>
            <if test="status != null">
                AND d.status = #{status}
            </if>
            ORDER BY d.parent_id, d.post_sort
            </script>
            """)
    List<SysDeptPo> selectDeptListWithLeader(@Param("deptName") String deptName, @Param("status") Integer status);
}
