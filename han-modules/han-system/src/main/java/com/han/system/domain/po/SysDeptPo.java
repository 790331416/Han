package com.han.system.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
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
    private Long leaderId;
    private String phone;
    private String email;
    private Integer status;

    /** 负责人姓名（非数据库字段，JOIN sys_user 查询） */
    @TableField(exist = false)
    private String leaderName;
}
