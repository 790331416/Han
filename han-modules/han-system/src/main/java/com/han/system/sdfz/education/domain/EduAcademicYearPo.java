package com.han.system.sdfz.education.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 租户统一学年；订单仍通过 edu_semester 关联到本表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_academic_year")
public class EduAcademicYearPo extends BizEntity {
    /** 学校级学年；历史全租户数据迁移完成前允许为空。 */
    private Long schoolId;
    private String yearCode;
    private String yearName;
    private LocalDate beginDate;
    private LocalDate endDate;
    private String status;
}
