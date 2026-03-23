package com.han.gen.domain;

import lombok.Data;

/**
 * 数据库表信息（information_schema 查询结果）
 */
@Data
public class DbTableInfo {
    private String tableName;
    private String tableComment;
    private String createTime;
    private String updateTime;
}
