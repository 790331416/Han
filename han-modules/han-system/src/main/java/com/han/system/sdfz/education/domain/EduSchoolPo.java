package com.han.system.sdfz.education.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_school")
public class EduSchoolPo extends BizEntity {
    private Long parentId;
    private String schoolCode;
    private String schoolName;
    private String schoolRole;
    private String sourceSystem;
    private String externalId;
    private String areaCode;
    private Integer status;
    private String syncHash;
    private LocalDateTime lastSyncTime;
}
