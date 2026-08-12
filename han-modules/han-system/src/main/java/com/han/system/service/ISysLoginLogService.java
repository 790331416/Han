package com.han.system.service;

import com.han.common.core.domain.PageResult;
import com.han.system.domain.po.SysLoginLogPo;
import com.han.system.domain.query.SysLoginLogQuery;

import java.util.List;

/**
 * 登录日志服务接口
 */
public interface ISysLoginLogService {

    /**
     * 按查询条件分页查询登录日志。
     */
    PageResult<SysLoginLogPo> selectPage(SysLoginLogQuery query);

    /**
     * 按查询条件查询登录日志（导出用，不分页但受 maxRows 上限约束）。
     *
     * @param maxRows 最多返回的条数
     */
    List<SysLoginLogPo> selectListForExport(SysLoginLogQuery query, int maxRows);

    void insertLoginLog(SysLoginLogPo po);

    void deleteByIds(List<Long> ids);

    void cleanAll();
}
