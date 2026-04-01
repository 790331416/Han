package com.han.gen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.gen.domain.DbColumnInfo;
import com.han.gen.domain.DbTableInfo;
import com.han.gen.domain.GenTable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GenTableMapper extends BaseMapper<GenTable> {

    /** 查询数据库中所有表（排除已导入的） */
    List<DbTableInfo> selectDbTableList(@Param("tableName") String tableName);

    /** 查询指定表的列信息 */
    List<DbColumnInfo> selectDbColumnsByTableName(@Param("tableName") String tableName);
}
