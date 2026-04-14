package com.han.gen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.gen.domain.*;
import com.han.gen.mapper.GenTableColumnMapper;
import com.han.gen.mapper.GenTableMapper;
import com.han.gen.util.GenUtils;
import com.han.gen.util.VelocityHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 代码生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenTableService {

    private final GenTableMapper genTableMapper;
    private final GenTableColumnMapper genTableColumnMapper;
    private final VelocityHelper velocityHelper;

    /**
     * 查询已导入的表列表（分页）
     */
    public PageResult<GenTable> selectGenTablePage(int pageNum, int pageSize, String tableName) {
        LambdaQueryWrapper<GenTable> wrapper = new LambdaQueryWrapper<GenTable>()
                .like(tableName != null && !tableName.isEmpty(), GenTable::getTableName, tableName)
                .orderByDesc(GenTable::getCreateTime);
        Page<GenTable> page = genTableMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    /**
     * 查询数据库中未导入的表列表
     */
    public List<DbTableInfo> selectDbTableList(String tableName) {
        return genTableMapper.selectDbTableList(tableName);
    }

    /**
     * 导入表（从数据库读取列信息并保存配置）
     */
    @Transactional(rollbackFor = Exception.class)
    public void importTable(List<String> tableNames) {
        for (String tableName : tableNames) {
            List<DbColumnInfo> columns = genTableMapper.selectDbColumnsByTableName(tableName);
            if (columns.isEmpty()) {
                throw new BusinessException("表[" + tableName + "]不存在或没有列");
            }

            String className = GenUtils.tableNameToClassName(tableName);

            GenTable table = GenTable.builder()
                    .tableName(tableName)
                    .tableComment(getTableComment(tableName))
                    .packageName("com.han.system")
                    .moduleName("system")
                    .businessName(className.substring(0, 1).toLowerCase() + className.substring(1))
                    .functionName(className)
                    .author("HanCloud")
                    .createTime(LocalDateTime.now())
                    .build();
            genTableMapper.insert(table);

            int sort = 1;
            for (DbColumnInfo col : columns) {
                GenTableColumn genCol = GenUtils.toGenColumn(col, table.getId(), sort++);
                genTableColumnMapper.insert(genCol);
            }
        }
    }

    private String getTableComment(String tableName) {
        List<DbTableInfo> tables = genTableMapper.selectDbTableList(null);
        return tables.stream()
                .filter(t -> tableName.equals(t.getTableName()))
                .map(DbTableInfo::getTableComment)
                .findFirst().orElse(tableName);
    }

    /**
     * 查询表详情（含列信息）
     */
    public GenTable selectGenTableById(Long id) {
        GenTable table = genTableMapper.selectById(id);
        if (table == null) {
            throw new BusinessException("生成表不存在");
        }
        List<GenTableColumn> columns = genTableColumnMapper.selectList(
                new LambdaQueryWrapper<GenTableColumn>()
                        .eq(GenTableColumn::getTableId, id)
                        .orderByAsc(GenTableColumn::getSort));
        table.setColumns(columns);
        return table;
    }

    /**
     * 更新表配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateGenTable(GenTable table) {
        table.setUpdateTime(LocalDateTime.now());
        genTableMapper.updateById(table);
        if (table.getColumns() != null) {
            for (GenTableColumn col : table.getColumns()) {
                genTableColumnMapper.updateById(col);
            }
        }
    }

    /**
     * 删除表配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteGenTable(Long id) {
        genTableMapper.deleteById(id);
        genTableColumnMapper.delete(
                new LambdaQueryWrapper<GenTableColumn>().eq(GenTableColumn::getTableId, id));
    }

    /**
     * 预览生成代码
     * @return Map<文件名, 代码内容>
     */
    public Map<String, String> previewCode(Long tableId) {
        GenTable table = selectGenTableById(tableId);
        return velocityHelper.renderTemplates(table);
    }

    /**
     * 生成代码并打包为 zip 字节数组
     */
    public byte[] generateCode(Long tableId) {
        GenTable table = selectGenTableById(tableId);
        Map<String, String> codes = velocityHelper.renderTemplates(table);
        return velocityHelper.toZipBytes(codes, table);
    }
}
