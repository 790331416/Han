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
    private String roomCode;
    private String roomName;
    private String roomType;
    private String sourceSystem;
    private String externalId;
    private Integer status;
}
