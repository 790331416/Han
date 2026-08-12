package com.han.system.sdfz.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.system.sdfz.order.domain.EduCourseOrderSubjectPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface EduCourseOrderSubjectMapper extends BaseMapper<EduCourseOrderSubjectPo> {

    /**
     * 把一张单现有的科目明细全部置为已删除。
     */
    @Update("UPDATE edu_course_order_subject SET del_flag = 1, update_time = CURRENT_TIMESTAMP"
            + " WHERE order_id = #{orderId} AND del_flag = 0")
    int deactivateByOrder(@Param("orderId") Long orderId);

    /**
     * 复活一条曾经被移除的科目明细，返回 0 表示这条明细从来没有过、需要新插。
     *
     * <p>唯一键 {@code (tenant_id, order_id, subject_id)} 不含 {@code del_flag}，
     * 而 {@code del_flag} 是逻辑删除列——所以「移除科目后又加回来」不能直接 insert，
     * 那会撞上还留在表里的历史行。这也是为什么这里要绕开 MyBatis-Plus 的逻辑删除自己写 SQL：
     * 逻辑删除会让常规查询看不见那一行，但唯一索引看得见。</p>
     */
    @Update("UPDATE edu_course_order_subject SET del_flag = 0, update_time = CURRENT_TIMESTAMP"
            + " WHERE order_id = #{orderId} AND subject_id = #{subjectId}")
    int reactivate(@Param("orderId") Long orderId, @Param("subjectId") Long subjectId);
}
