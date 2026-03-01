package com.han.system.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.TreeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDeptPo extends TreeEntity<SysDeptPo> {
    private String deptName;
    private Integer orderNum;
    private String leader;
    private String phone;
    private String email;
    private Integer status;
}
