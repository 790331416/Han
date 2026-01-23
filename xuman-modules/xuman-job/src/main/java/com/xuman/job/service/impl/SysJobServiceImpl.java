package com.xuman.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xuman.common.core.domain.PageResult;
import com.xuman.common.mybatis.util.PageHelper;
import com.xuman.job.convert.SysJobConvert;
import com.xuman.job.domain.dto.JobDTO;
import com.xuman.job.domain.dto.JobQueryDTO;
import com.xuman.job.domain.entity.SysJob;
import com.xuman.job.domain.vo.JobVO;
import com.xuman.job.mapper.SysJobMapper;
import com.xuman.job.service.SysJobService;
import com.xuman.job.util.QuartzJobUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 定时任务服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysJobServiceImpl implements SysJobService {

    private final SysJobMapper jobMapper;
    private final Scheduler scheduler;
    private final SysJobConvert jobConvert;

    @Override
    public PageResult<JobVO> listJob(JobQueryDTO dto) {
        Page<SysJob> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<SysJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(dto.getJobName()), SysJob::getJobName, dto.getJobName())
                .eq(StringUtils.hasText(dto.getJobGroup()), SysJob::getJobGroup, dto.getJobGroup())
                .eq(StringUtils.hasText(dto.getStatus()), SysJob::getStatus, dto.getStatus())
                .like(StringUtils.hasText(dto.getInvokeTarget()), SysJob::getInvokeTarget, dto.getInvokeTarget())
                .orderByDesc(SysJob::getCreateTime);
        
        Page<SysJob> result = jobMapper.selectPage(page, wrapper);
        return PageHelper.build(result, this::convertToVO);
    }

    @Override
    public List<JobVO> listAllJob() {
        List<SysJob> jobs = jobMapper.selectList(null);
        return jobs.stream().map(this::convertToVO).toList();
    }

    @Override
    public JobVO getJobById(Long jobId) {
        SysJob job = jobMapper.selectById(jobId);
        return job != null ? convertToVO(job) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createJob(JobDTO dto) throws SchedulerException {
        SysJob job = jobConvert.toEntity(dto);
        jobMapper.insert(job);
        
        // 添加到Quartz调度器
        QuartzJobUtils.createScheduleJob(scheduler, job);
        log.info("创建定时任务成功: {}", job.getJobName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateJob(JobDTO dto) throws SchedulerException {
        SysJob job = jobMapper.selectById(dto.getJobId());
        if (job == null) {
            throw new RuntimeException("任务不存在");
        }
        
        jobConvert.updateEntity(dto, job);
        jobMapper.updateById(job);
        
        // 更新Quartz调度器
        QuartzJobUtils.updateScheduleJob(scheduler, job);
        log.info("更新定时任务成功: {}", job.getJobName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteJob(Long jobId) throws SchedulerException {
        SysJob job = jobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        
        jobMapper.deleteById(jobId);
        QuartzJobUtils.deleteScheduleJob(scheduler, job);
        log.info("删除定时任务成功: {}", job.getJobName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteJobByIds(Long[] jobIds) throws SchedulerException {
        for (Long jobId : jobIds) {
            deleteJob(jobId);
        }
    }

    @Override
    public void pauseJob(Long jobId) throws SchedulerException {
        SysJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new RuntimeException("任务不存在");
        }
        
        job.setStatus("1");
        jobMapper.updateById(job);
        QuartzJobUtils.pauseJob(scheduler, job);
        log.info("暂停定时任务: {}", job.getJobName());
    }

    @Override
    public void resumeJob(Long jobId) throws SchedulerException {
        SysJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new RuntimeException("任务不存在");
        }
        
        job.setStatus("0");
        jobMapper.updateById(job);
        QuartzJobUtils.resumeJob(scheduler, job);
        log.info("恢复定时任务: {}", job.getJobName());
    }

    @Override
    public void runJob(Long jobId) throws SchedulerException {
        SysJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new RuntimeException("任务不存在");
        }
        
        QuartzJobUtils.runJobNow(scheduler, job);
        log.info("立即执行定时任务: {}", job.getJobName());
    }

    @Override
    public void changeStatus(Long jobId, String status) throws SchedulerException {
        if ("0".equals(status)) {
            resumeJob(jobId);
        } else if ("1".equals(status)) {
            pauseJob(jobId);
        }
    }

    @Override
    public boolean checkCronExpression(String cronExpression) {
        return CronExpression.isValidExpression(cronExpression);
    }

    private JobVO convertToVO(SysJob job) {
        JobVO vo = jobConvert.toVO(job);
        
        // 获取下次执行时间
        try {
            TriggerKey triggerKey = QuartzJobUtils.getTriggerKey(job);
            Trigger trigger = scheduler.getTrigger(triggerKey);
            if (trigger != null && trigger.getNextFireTime() != null) {
                vo.setNextFireTime(trigger.getNextFireTime().toInstant()
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            }
        } catch (SchedulerException e) {
            log.warn("获取任务下次执行时间失败: {}", e.getMessage());
        }
        
        return vo;
    }
}
