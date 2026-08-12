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
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    /** 单次导入的表数量上限，避免一个请求把整库拖进同一个事务 */
    private static final int MAX_IMPORT_BATCH = 50;

    /**
     * 查询已导入的表列表（分页）
     */
    public PageResult<GenTable> selectGenTablePage(int pageNum, int pageSize, String tableName) {
        LambdaQueryWrapper<GenTable> wrapper = new LambdaQueryWrapper<GenTable>()
                .like(StringUtils.hasText(tableName), GenTable::getTableName, escapeLike(tableName))
                .orderByDesc(GenTable::getCreateTime);
        Page<GenTable> page = genTableMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    /**
     * 查询数据库中未导入的表列表
     */
    public List<DbTableInfo> selectDbTableList(String tableName) {
        return genTableMapper.selectDbTableList(escapeLike(tableName));
    }

    /**
     * 导入表（从数据库读取列信息并保存配置）
     */
    @Transactional(rollbackFor = Exception.class)
    public void importTable(List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            throw new BusinessException("请至少选择一张表");
        }
        if (tableNames.size() > MAX_IMPORT_BATCH) {
            throw new BusinessException("单次最多导入 " + MAX_IMPORT_BATCH + " 张表，当前选择了 " + tableNames.size() + " 张");
        }
        List<String> distinctNames = tableNames.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (distinctNames.isEmpty()) {
            throw new BusinessException("请至少选择一张表");
        }

        List<String> imported = genTableMapper.selectList(
                        new LambdaQueryWrapper<GenTable>().in(GenTable::getTableName, distinctNames))
                .stream().map(GenTable::getTableName).toList();
        if (!imported.isEmpty()) {
            throw new BusinessException("表" + imported + "已导入过，请先删除已有配置再重新导入");
        }

        // 表注释一次性查出来建索引，避免每导一张表就全量扫一次 pg_class
        Map<String, String> commentIndex = genTableMapper.selectDbTableList(null).stream()
                .filter(t -> StringUtils.hasText(t.getTableName()) && StringUtils.hasText(t.getTableComment()))
                .collect(Collectors.toMap(DbTableInfo::getTableName, DbTableInfo::getTableComment, (a, b) -> a));

        for (String tableName : distinctNames) {
            List<DbColumnInfo> columns = genTableMapper.selectDbColumnsByTableName(tableName);
            if (columns.isEmpty()) {
                throw new BusinessException("表[" + tableName + "]不存在或没有列");
            }

            String className = GenUtils.tableNameToClassName(tableName);

            GenTable table = GenTable.builder()
                    .tableName(tableName)
                    .tableComment(commentIndex.getOrDefault(tableName, tableName))
                    .packageName("com.han.system")
                    .moduleName("system")
                    .businessName(className.substring(0, 1).toLowerCase() + className.substring(1))
                    .functionName(className)
                    .author("HanCloud")
                    .parentMenuId(0L)
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

    /**
     * 转义 LIKE 通配符。
     *
     * <p>PostgreSQL 的 LIKE 默认以反斜杠为转义符，参数已是预编译绑定值，这里只做语义收敛：
     * 让用户输入的 {@code %} / {@code _} 按字面量匹配，而不是当通配符用。
     */
    private String escapeLike(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
     * 更新表配置。
     *
     * <p>只信任请求体里的列 ID 会让人改到别的生成配置上（同租户内的水平越权），
     * 因此先按 tableId 查出该表的合法列 ID 集合，请求里出现集合外的列就直接拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateGenTable(GenTable table) {
        if (table == null || table.getId() == null) {
            throw new BusinessException("生成表主键不能为空");
        }
        GenTable existing = genTableMapper.selectById(table.getId());
        if (existing == null) {
            throw new BusinessException("生成表不存在");
        }
        table.setUpdateTime(LocalDateTime.now());
        genTableMapper.updateById(table);

        List<GenTableColumn> columns = table.getColumns();
        if (columns == null || columns.isEmpty()) {
            return;
        }
        Set<Long> ownedColumnIds = genTableColumnMapper.selectList(
                        new LambdaQueryWrapper<GenTableColumn>().eq(GenTableColumn::getTableId, table.getId()))
                .stream().map(GenTableColumn::getId).collect(Collectors.toSet());
        for (GenTableColumn col : columns) {
            if (col.getId() == null || !ownedColumnIds.contains(col.getId())) {
                throw new BusinessException("列配置[" + col.getId() + "]不属于表[" + existing.getTableName() + "]");
            }
            col.setTableId(table.getId());
            genTableColumnMapper.updateById(col);
        }
    }

    /**
     * 删除表配置。
     *
     * <p>先删子表再删主表：`gen_table_column.table_id` 上虽然有 ON DELETE CASCADE，
     * 但显式按依赖顺序删除可以让逻辑脱离外键约束也成立。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteGenTable(Long id) {
        genTableColumnMapper.delete(
                new LambdaQueryWrapper<GenTableColumn>().eq(GenTableColumn::getTableId, id));
        genTableMapper.deleteById(id);
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
