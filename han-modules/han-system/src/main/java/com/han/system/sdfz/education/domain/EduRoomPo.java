package com.han.system.sdfz.education.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_room")
public class EduRoomPo extends BizEntity {
    private Long schoolId;
    private Long parentId;
    private String ancestors;
    private Integer nodeLevel;
    private Integer sort;
    private String nodeType;
    private String roomCode;
    private String roomName;
    private String aliasName;
    private String roomType;
    private Integer capacity;
    private java.math.BigDecimal longitude;
    private java.math.BigDecimal latitude;
    private String dataSource;
    private String sourceSystem;
    private String externalId;
    private Integer status;
}
