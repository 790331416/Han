package com.han.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.log.domain.OperLogEvent;
import com.han.common.log.service.IOperLogService;
import com.han.system.domain.po.SysOperLogPo;
import com.han.system.domain.query.SysOperLogQuery;
import com.han.system.mapper.SysOperLogMapper;
import com.han.system.service.ISysOperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 操作日志服务实现
 * <p>
 * 同时实现 IOperLogService（SPI 接口），供 OperLogAspect 异步写入。
 */
@Service
@RequiredArgsConstructor
public class SysOperLogServiceImpl implements ISysOperLogService, IOperLogService {

    /** 单页最大条数，防止 pageSize 被传成超大值拖库 */
    private static final int MAX_PAGE_SIZE = 200;

    private final SysOperLogMapper operLogMapper;

    // ==================== ISysOperLogService ====================

    @Override
    public PageResult<SysOperLogPo> selectPage(SysOperLogQuery query) {
        Page<SysOperLogPo> page = operLogMapper.selectPage(
                new Page<>(clampPageNum(query.getPageNum()), clampPageSize(query.getPageSize())),
                buildQueryWrapper(query)
        );
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    private static long clampPageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1L : pageNum;
    }

    private static long clampPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    @Override
    public List<SysOperLogPo> selectListForExport(SysOperLogQuery query, int maxRows) {
        return operLogMapper.selectList(buildQueryWrapper(query).last("LIMIT " + maxRows));
    }

    @Override
    public SysOperLogPo selectById(Long id) {
        return operLogMapper.selectById(id);
    }

    @Override
    public void insertOperLog(SysOperLogPo operLog) {
        operLogMapper.insert(operLog);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        operLogMapper.deleteByIds(ids);
    }

    @Override
    public void cleanAll() {
        operLogMapper.delete(new LambdaQueryWrapper<>());
    }

    // ==================== IOperLogService (SPI) ====================

    @Override
    public void recordOperLog(OperLogEvent event) {
        SysOperLogPo po = SysOperLogPo.builder()
                .tenantId(event.getTenantId())
                .module(event.getModule())
                .operType(event.getOperType())
                .operName(event.getOperName())
                .operUserId(event.getOperUserId())
                .deptName(event.getDeptName())
                .operUrl(event.getOperUrl())
                .operIp(event.getOperIp())
                .operLocation(event.getOperLocation())
                .requestMethod(event.getRequestMethod())
                .operParam(event.getOperParam())
                .jsonResult(event.getJsonResult())
                .status(event.getStatus())
                .errorMsg(event.getErrorMsg())
                .costTime(event.getCostTime())
                .operTime(event.getOperTime())
                .build();
        operLogMapper.insert(po);
    }

    // ==================== 私有方法 ====================

    private LambdaQueryWrapper<SysOperLogPo> buildQueryWrapper(SysOperLogQuery query) {
        return new LambdaQueryWrapper<SysOperLogPo>()
                .like(query.getModule() != null && !query.getModule().isEmpty(),
                        SysOperLogPo::getModule, query.getModule())
                .eq(query.getOperType() != null, SysOperLogPo::getOperType, query.getOperType())
                .like(query.getOperName() != null && !query.getOperName().isEmpty(),
                        SysOperLogPo::getOperName, query.getOperName())
                .eq(query.getStatus() != null, SysOperLogPo::getStatus, query.getStatus())
                .ge(query.getBeginTime() != null, SysOperLogPo::getOperTime, query.getBeginTime())
                .le(query.getEndTime() != null, SysOperLogPo::getOperTime, query.getEndTime())
                .orderByDesc(SysOperLogPo::getOperTime);
    }
}
