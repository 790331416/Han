package com.han.system.service;

import com.han.common.core.domain.PageResult;
import com.han.system.domain.po.SysOperLogPo;
import com.han.system.domain.query.SysOperLogQuery;

import java.util.List;

/**
 * 操作日志服务接口
 */
public interface ISysOperLogService {

    /**
     * 分页查询操作日志
     */
    PageResult<SysOperLogPo> selectPage(SysOperLogQuery query);

    /**
     * 查询操作日志（导出用，不分页，按 maxRows 截断）
     *
     * <p>分页插件 maxLimit 是 500，导出复用分页会被静默截断成 500 条。
     */
    List<SysOperLogPo> selectListForExport(SysOperLogQuery query, int maxRows);

    /**
     * 根据ID查询操作日志
     */
    SysOperLogPo selectById(Long id);

    /**
     * 新增操作日志
     */
    void insertOperLog(SysOperLogPo operLog);

    /**
     * 批量删除操作日志
     */
    void deleteByIds(List<Long> ids);

    /**
     * 清空操作日志
     */
    void cleanAll();
}
