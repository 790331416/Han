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

    /** 显示顺序: sys_dept 排序列为 post_sort */
    @TableField("post_sort")
    private Integer orderNum;

    /** 覆盖基类 TreeEntity.sort: sys_dept 无 sort 列(排序用 post_sort), 标记非表字段避免 INSERT/UPDATE 报 column 不存在 */
    @TableField(exist = false)
    private Integer sort;

    private Long leaderId;
    private String phone;
    private String email;
    private Integer status;

    /** 负责人姓名（非数据库字段，JOIN sys_user 查询） */
    @TableField(exist = false)
    private String leaderName;
}
