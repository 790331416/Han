package com.xuman.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xuman.common.core.domain.PageResult;
import com.xuman.common.mybatis.util.PageHelper;
import com.xuman.job.convert.SysJobLogConvert;
import com.xuman.job.domain.query.JobLogQuery;
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
    public PageResult<JobLogVO> listJobLog(JobLogQuery query) {
        Page<SysJobLog> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysJobLog> wrapper = new LambdaQueryWrapper<>();
        
        SysJobLog base = query.getBase();
        if (base != null) {
            wrapper.like(StringUtils.hasText(base.getJobName()), SysJobLog::getJobName, base.getJobName())
                    .eq(StringUtils.hasText(base.getJobGroup()), SysJobLog::getJobGroup, base.getJobGroup())
                    .eq(StringUtils.hasText(base.getStatus()), SysJobLog::getStatus, base.getStatus());
        }
        wrapper.ge(query.getBeginTime() != null, SysJobLog::getStartTime, query.getBeginTime())
                .le(query.getEndTime() != null, SysJobLog::getStopTime, query.getEndTime())
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
