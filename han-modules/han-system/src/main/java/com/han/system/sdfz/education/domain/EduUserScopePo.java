package com.han.system.sdfz.education.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 教育管理用户对区域或教育组织的显式数据范围。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_user_scope")
public class EduUserScopePo extends BizEntity {
    private Long userId;
    private String scopeType;
    private Long scopeId;
    private Integer includeChildren;
    private Integer status;
}
