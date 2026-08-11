package com.han.system.sdfz.education.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_person")
public class EduPersonPo extends BizEntity {
    private Long userId;
    private Long schoolId;
    private String personNo;
    private String personName;
    private String personType;
    private String phone;
    private String sourceSystem;
    private String externalUserId;
    private String externalIdentityId;
    private Integer status;
    private String syncHash;
    private LocalDateTime lastSyncTime;
}
