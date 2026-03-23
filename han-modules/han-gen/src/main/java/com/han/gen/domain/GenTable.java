package com.han.gen.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 代码生成 — 业务表配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("gen_table")
public class GenTable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 表名称 */
    private String tableName;

    /** 表描述 */
    private String tableComment;

    /** 生成包路径 */
    private String packageName;

    /** 生成模块名 */
    private String moduleName;

    /** 生成业务名 */
    private String businessName;

    /** 生成功能名 */
    private String functionName;

    /** 作者 */
    private String author;

    /** 父菜单ID */
    private Long parentMenuId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 列信息（非DB字段） */
    @TableField(exist = false)
    private List<GenTableColumn> columns;
}
