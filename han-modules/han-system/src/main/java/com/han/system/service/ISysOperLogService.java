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
