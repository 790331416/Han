package com.han.system.service;

import com.han.common.core.domain.PageResult;
import com.han.system.domain.po.SysLoginLogPo;

import java.util.List;

/**
 * 登录日志服务接口
 */
public interface ISysLoginLogService {

    PageResult<SysLoginLogPo> selectPage(Integer pageNum, Integer pageSize);

    void insertLoginLog(SysLoginLogPo po);

    void deleteByIds(List<Long> ids);

    void cleanAll();
}
