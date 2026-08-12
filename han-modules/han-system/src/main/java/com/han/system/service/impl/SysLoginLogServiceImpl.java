package com.han.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.system.domain.po.SysLoginLogPo;
import com.han.system.domain.query.SysLoginLogQuery;
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
    public PageResult<SysLoginLogPo> selectPage(SysLoginLogQuery query) {
        Page<SysLoginLogPo> page = loginLogMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                buildQueryWrapper(query)
        );
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    public List<SysLoginLogPo> selectListForExport(SysLoginLogQuery query, int maxRows) {
        return loginLogMapper.selectList(buildQueryWrapper(query).last("LIMIT " + maxRows));
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

    // ==================== 私有方法 ====================

    /**
     * 条件拼装照抄同模块 {@code SysOperLogServiceImpl.buildQueryWrapper}，
     * 此前这里直接返回全量分页，前端三个搜索框点了完全没反应。
     */
    private LambdaQueryWrapper<SysLoginLogPo> buildQueryWrapper(SysLoginLogQuery query) {
        return new LambdaQueryWrapper<SysLoginLogPo>()
                .like(query.getUsername() != null && !query.getUsername().isEmpty(),
                        SysLoginLogPo::getUsername, query.getUsername())
                .like(query.getIpAddr() != null && !query.getIpAddr().isEmpty(),
                        SysLoginLogPo::getIpAddr, query.getIpAddr())
                .eq(query.getStatus() != null, SysLoginLogPo::getStatus, query.getStatus())
                .ge(query.getBeginTime() != null, SysLoginLogPo::getLoginTime, query.getBeginTime())
                .le(query.getEndTime() != null, SysLoginLogPo::getLoginTime, query.getEndTime())
                .orderByDesc(SysLoginLogPo::getLoginTime);
    }
}
