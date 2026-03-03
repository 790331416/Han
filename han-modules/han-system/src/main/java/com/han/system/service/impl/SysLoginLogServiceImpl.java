package com.han.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.system.domain.po.SysLoginLogPo;
import com.han.system.mapper.SysLoginLogMapper;
import com.han.system.service.ISysLoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 登录日志服务实现
 */
@Service
@RequiredArgsConstructor
public class SysLoginLogServiceImpl implements ISysLoginLogService {

    private final SysLoginLogMapper loginLogMapper;

    @Override
    public PageResult<SysLoginLogPo> selectPage(Integer pageNum, Integer pageSize) {
        Page<SysLoginLogPo> page = loginLogMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SysLoginLogPo>().orderByDesc(SysLoginLogPo::getLoginTime)
        );
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    public void insertLoginLog(SysLoginLogPo po) {
        loginLogMapper.insert(po);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        loginLogMapper.deleteByIds(ids);
    }

    @Override
    public void cleanAll() {
        loginLogMapper.delete(new LambdaQueryWrapper<>());
    }
}
