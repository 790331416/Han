package com.han.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.mybatis.util.PageHelper;
import com.han.job.converter.SysJobConverter;
import com.han.job.domain.dto.JobDTO;
import com.han.job.domain.po.SysJobPo;
import com.han.job.domain.query.JobQuery;
import com.han.job.domain.vo.JobVO;
import com.han.job.mapper.SysJobMapper;
import com.han.job.service.ISysJobService;
import com.han.job.util.QuartzJobUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
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
public class SysJobServiceImpl implements ISysJobService {

    private final SysJobMapper jobMapper;
    private final Scheduler scheduler;
    private final SysJobConverter jobConverter;

    @Override
    public PageResult<JobDTO> listJob(JobQuery query) {
        Page<SysJobPo> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysJobPo> wrapper = new LambdaQueryWrapper<>();

        wrapper.like(StringUtils.hasText(query.getJobName()), SysJobPo::getJobName, query.getJobName())
                .eq(StringUtils.hasText(query.getJobGroup()), SysJobPo::getJobGroup, query.getJobGroup())
                .eq(StringUtils.hasText(query.getStatus()), SysJobPo::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getInvokeTarget()), SysJobPo::getInvokeTarget, query.getInvokeTarget())
                .orderByDesc(SysJobPo::getCreateTime);

        Page<SysJobPo> result = jobMapper.selectPage(page, wrapper);
        return PageHelper.build(result, jobConverter::toDto);
    }

    @Override
    public List<JobVO> listAllJob() {
        List<SysJobPo> jobs = jobMapper.selectList(null);
        return jobConverter.toVoList(jobs);
    }

    @Override
    public JobDTO getJobById(Long jobId) {
        SysJobPo job = jobMapper.selectById(jobId);
        return job != null ? jobConverter.toDto(job) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createJob(JobDTO dto) throws SchedulerException {
        SysJobPo job = jobConverter.toPo(dto);
        jobMapper.insert(job);
        QuartzJobUtils.createScheduleJob(scheduler, job);
        log.info("创建定时任务成功: {}", job.getJobName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateJob(JobDTO dto) throws SchedulerException {
        Long jobId = dto.getJobId();
        SysJobPo job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new RuntimeException("任务不存在");
        }

        SysJobPo updated = jobConverter.toPo(dto);
        job.setJobName(updated.getJobName());
        job.setJobGroup(updated.getJobGroup());
        job.setInvokeTarget(updated.getInvokeTarget());
        job.setCronExpression(updated.getCronExpression());
        job.setMisfirePolicy(updated.getMisfirePolicy());
        job.setConcurrent(updated.getConcurrent());
        job.setStatus(updated.getStatus());
        job.setRemark(updated.getRemark());
        jobMapper.updateById(job);

        QuartzJobUtils.updateScheduleJob(scheduler, job);
        log.info("更新定时任务成功: {}", job.getJobName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteJob(Long jobId) throws SchedulerException {
        SysJobPo job = jobMapper.selectById(jobId);
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

    @Override
    public List<JobDTO> selectListScope(JobQuery query) {
        return selectList(query);
    }

    @Override
    public List<JobDTO> selectList(JobQuery query) {
        LambdaQueryWrapper<SysJobPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getJobName()), SysJobPo::getJobName, query.getJobName())
                .eq(StringUtils.hasText(query.getJobGroup()), SysJobPo::getJobGroup, query.getJobGroup())
                .eq(StringUtils.hasText(query.getStatus()), SysJobPo::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getInvokeTarget()), SysJobPo::getInvokeTarget, query.getInvokeTarget());
        List<SysJobPo> jobs = jobMapper.selectList(wrapper);
        return jobConverter.toDtoList(jobs);
    }

    @Override
    public JobDTO selectById(Long id) {
        return getJobById(id);
    }

    @Override
    public List<JobDTO> selectByIds(List<Long> ids) {
        List<SysJobPo> jobs = jobMapper.selectBatchIds(ids);
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
        SysJobPo job = jobMapper.selectById(jobId);
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
        SysJobPo job = jobMapper.selectById(jobId);
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
        SysJobPo job = jobMapper.selectById(jobId);
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

    private JobVO convertToVO(SysJobPo job) {
        JobVO vo = jobConverter.toVo(job);
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
