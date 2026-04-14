package com.han.gen.domain;

import lombok.Data;

/**
 * 数据库列信息（information_schema 查询结果）
 */
@Data
public class DbColumnInfo {
    private String columnName;
    private String columnComment;
    private String columnType;
    private String isNullable;
    private String columnKey;
    private String columnDefault;
    private Integer ordinalPosition;
}
