package com.han.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.mybatis.util.PageHelper;
import com.han.job.converter.SysJobLogConverter;
import com.han.job.domain.query.JobLogQuery;
import com.han.job.domain.po.SysJobLogPo;
import com.han.job.domain.vo.JobLogVO;
import com.han.job.mapper.SysJobLogMapper;
import com.han.job.service.ISysJobLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * 任务日志服务实现
 */
@Service
@RequiredArgsConstructor
public class SysJobLogServiceImpl implements ISysJobLogService {

    private final SysJobLogMapper jobLogMapper;
    private final SysJobLogConverter jobLogConvert;

    @Override
    public PageResult<JobLogVO> listJobLog(JobLogQuery query) {
        Page<SysJobLogPo> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysJobLogPo> wrapper = new LambdaQueryWrapper<>();
        
        SysJobLogPo base = query.getBase();
        if (base != null) {
            wrapper.like(StringUtils.hasText(base.getJobName()), SysJobLogPo::getJobName, base.getJobName())
                    .eq(StringUtils.hasText(base.getJobGroup()), SysJobLogPo::getJobGroup, base.getJobGroup())
                    .eq(StringUtils.hasText(base.getStatus()), SysJobLogPo::getStatus, base.getStatus());
        }
        wrapper.ge(query.getBeginTime() != null, SysJobLogPo::getStartTime, query.getBeginTime())
                .le(query.getEndTime() != null, SysJobLogPo::getStopTime, query.getEndTime())
                .orderByDesc(SysJobLogPo::getCreateTime);
        
        Page<SysJobLogPo> result = jobLogMapper.selectPage(page, wrapper);
        return PageHelper.build(result, jobLogConvert::toVO);
    }

    @Override
    public JobLogVO getJobLogById(Long jobLogId) {
        SysJobLogPo log = jobLogMapper.selectById(jobLogId);
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
