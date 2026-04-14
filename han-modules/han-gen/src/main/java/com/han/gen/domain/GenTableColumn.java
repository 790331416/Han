package com.han.gen.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成 — 业务表字段配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("gen_table_column")
public class GenTableColumn {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 归属表ID */
    private Long tableId;

    /** 列名称 */
    private String columnName;

    /** 列描述 */
    private String columnComment;

    /** 列类型（varchar/int8/timestamp 等） */
    private String columnType;

    /** Java 类型（String/Long/LocalDateTime 等） */
    private String javaType;

    /** Java 字段名（驼峰） */
    private String javaField;

    /** 是否主键（1是） */
    private Integer isPk;

    /** 是否自增（1是） */
    private Integer isIncrement;

    /** 是否必填（1是） */
    private Integer isRequired;

    /** 是否为插入字段（1是） */
    private Integer isInsert;

    /** 是否编辑字段（1是） */
    private Integer isEdit;

    /** 是否列表字段（1是） */
    private Integer isList;

    /** 是否查询字段（1是） */
    private Integer isQuery;

    /** 查询方式（EQ/LIKE/BETWEEN 等） */
    private String queryType;

    /** 显示类型（input/select/datetime/textarea/radio/checkbox） */
    private String htmlType;

    /** 字典类型 */
    private String dictType;

    /** 排序 */
    private Integer sort;
}
