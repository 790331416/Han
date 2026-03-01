package com.han.system.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_post")
public class SysPostPo extends BizEntity {
    private String postCode;
    private String postName;
    private Integer postSort;
    private Integer status;
}
