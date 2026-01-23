package com.xuman.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xuman.common.core.domain.PageResult;
import com.xuman.common.mybatis.util.PageHelper;
import com.xuman.job.convert.SysJobLogConvert;
import com.xuman.job.domain.dto.JobLogQueryDTO;
import com.xuman.job.domain.entity.SysJobLog;
import com.xuman.job.domain.vo.JobLogVO;
import com.xuman.job.mapper.SysJobLogMapper;
import com.xuman.job.service.SysJobLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * 任务日志服务实现
 */
@Service
@RequiredArgsConstructor
public class SysJobLogServiceImpl implements SysJobLogService {

    private final SysJobLogMapper jobLogMapper;
    private final SysJobLogConvert jobLogConvert;

    @Override
    public PageResult<JobLogVO> listJobLog(JobLogQueryDTO dto) {
        Page<SysJobLog> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<SysJobLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(dto.getJobName()), SysJobLog::getJobName, dto.getJobName())
                .eq(StringUtils.hasText(dto.getJobGroup()), SysJobLog::getJobGroup, dto.getJobGroup())
                .eq(StringUtils.hasText(dto.getStatus()), SysJobLog::getStatus, dto.getStatus())
                .ge(dto.getStartTime() != null, SysJobLog::getStartTime, dto.getStartTime())
                .le(dto.getEndTime() != null, SysJobLog::getStopTime, dto.getEndTime())
                .orderByDesc(SysJobLog::getCreateTime);
        
        Page<SysJobLog> result = jobLogMapper.selectPage(page, wrapper);
        return PageHelper.build(result, jobLogConvert::toVO);
    }

    @Override
    public JobLogVO getJobLogById(Long jobLogId) {
        SysJobLog log = jobLogMapper.selectById(jobLogId);
        return log != null ? jobLogConvert.toVO(log) : null;
    }

    @Override
    public void deleteJobLog(Long jobLogId) {
        jobLogMapper.deleteById(jobLogId);
    }

    @Override
    public void deleteJobLogByIds(Long[] jobLogIds) {
        jobLogMapper.deleteByIds(Arrays.asList(jobLogIds));
    }

    @Override
    public void cleanJobLog() {
        jobLogMapper.delete(null);
    }
}
