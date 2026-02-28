package com.han.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.mybatis.util.PageHelper;
import com.han.job.convert.JobConverter;
import com.han.job.domain.dto.JobDTO;
import com.han.job.domain.query.JobQuery;
import com.han.job.domain.entity.SysJob;
import com.han.job.domain.vo.JobVO;
import com.han.job.mapper.SysJobMapper;
import com.han.job.service.SysJobService;
import com.han.job.util.QuartzJobUtils;
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
    private final JobConverter jobConverter;

    @Override
    public PageResult<JobDTO> listJob(JobQuery query) {
        Page<SysJob> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysJob> wrapper = new LambdaQueryWrapper<>();
        
        SysJob base = query.getBase();
        if (base != null) {
            wrapper.like(StringUtils.hasText(base.getJobName()), SysJob::getJobName, base.getJobName())
                    .eq(StringUtils.hasText(base.getJobGroup()), SysJob::getJobGroup, base.getJobGroup())
                    .eq(StringUtils.hasText(base.getStatus()), SysJob::getStatus, base.getStatus())
                    .like(StringUtils.hasText(base.getInvokeTarget()), SysJob::getInvokeTarget, base.getInvokeTarget());
        }
        wrapper.orderByDesc(SysJob::getCreateTime);
        
        Page<SysJob> result = jobMapper.selectPage(page, wrapper);
        return PageHelper.build(result, jobConverter::toDto);
    }

    @Override
    public List<JobVO> listAllJob() {
        List<SysJob> jobs = jobMapper.selectList(null);
        return jobConverter.toVoList(jobs);
    }

    @Override
    public JobDTO getJobById(Long jobId) {
        SysJob job = jobMapper.selectById(jobId);
        return job != null ? jobConverter.toDto(job) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createJob(JobDTO dto) throws SchedulerException {
        SysJob job = jobConverter.toEntity(dto);
        jobMapper.insert(job);
        
        // 添加到Quartz调度器
        QuartzJobUtils.createScheduleJob(scheduler, job);
        log.info("创建定时任务成功: {}", job.getJobName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateJob(JobDTO dto) throws SchedulerException {
        SysJob job = jobMapper.selectById(dto.getBase() != null ? dto.getBase().getJobId() : null);
        if (job == null) {
            throw new RuntimeException("任务不存在");
        }
        
        // 使用MapStruct转换并更新
        SysJob updated = jobConverter.toEntity(dto);
        job.setJobName(updated.getJobName());
        job.setJobGroup(updated.getJobGroup());
        job.setInvokeTarget(updated.getInvokeTarget());
        job.setCronExpression(updated.getCronExpression());
        job.setMisfirePolicy(updated.getMisfirePolicy());
        job.setConcurrent(updated.getConcurrent());
        job.setStatus(updated.getStatus());
        job.setRemark(updated.getRemark());
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
    public int deleteByIds(List<Long> ids) {
        try {
            deleteJobByIds(ids.toArray(new Long[0]));
            return ids.size();
        } catch (SchedulerException e) {
            throw new RuntimeException("批量删除任务失败", e);
        }
    }
    
    // ================ 实现 IBaseService 的其他方法 ================
    
    @Override
    public List<JobDTO> selectListScope(JobQuery query) {
        return selectList(query);
    }
    
    @Override
    public List<JobDTO> selectList(JobQuery query) {
        LambdaQueryWrapper<SysJob> wrapper = new LambdaQueryWrapper<>();
        SysJob base = query.getBase();
        if (base != null) {
            wrapper.like(StringUtils.hasText(base.getJobName()), SysJob::getJobName, base.getJobName())
                    .eq(StringUtils.hasText(base.getJobGroup()), SysJob::getJobGroup, base.getJobGroup())
                    .eq(StringUtils.hasText(base.getStatus()), SysJob::getStatus, base.getStatus())
                    .like(StringUtils.hasText(base.getInvokeTarget()), SysJob::getInvokeTarget, base.getInvokeTarget());
        }
        List<SysJob> jobs = jobMapper.selectList(wrapper);
        return jobConverter.toDtoList(jobs);
    }
    
    @Override
    public JobDTO selectById(Long id) {
        return getJobById(id);
    }
    
    @Override
    public List<JobDTO> selectByIds(List<Long> ids) {
        List<SysJob> jobs = jobMapper.selectBatchIds(ids);
        return jobConverter.toDtoList(jobs);
    }
    
    @Override
    public int insert(JobDTO dto) {
        try {
            createJob(dto);
            return 1;
        } catch (SchedulerException e) {
            throw new RuntimeException("创建任务失败", e);
        }
    }
    
    @Override
    public int update(JobDTO dto) {
        try {
            updateJob(dto);
            return 1;
        } catch (SchedulerException e) {
            throw new RuntimeException("更新任务失败", e);
        }
    }
    
    @Override
    public int deleteById(Long id) {
        try {
            deleteJob(id);
            return 1;
        } catch (SchedulerException e) {
            throw new RuntimeException("删除任务失败", e);
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
        JobVO vo = jobConverter.toVo(job);
        
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
