package com.han.system.sdfz.education.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_semester")
public class EduSemesterPo extends BizEntity {

    /** 学校级学期；历史全租户数据迁移完成前允许为空。 */
    private Long schoolId;
    private Long academicYearId;
    private String semesterCode;
    private String semesterName;
    private LocalDate beginDate;
    private LocalDate endDate;

    /** 是否当前学期，人工标记，与 lifecycleStatus 无关。 */
    private Integer currentFlag;

    /** 记录启用状态：0 正常 / 1 停用。 */
    private Integer status;

    /** 学期阶段：NOT_STARTED / IN_PROGRESS / FINISHED，由定时任务按日期推进。 */
    private String lifecycleStatus;
}
