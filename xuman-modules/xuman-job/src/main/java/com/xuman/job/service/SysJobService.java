package com.xuman.job.service;

import com.xuman.common.core.domain.PageResult;
import com.xuman.job.domain.dto.JobDTO;
import com.xuman.job.domain.dto.JobQueryDTO;
import com.xuman.job.domain.vo.JobVO;
import org.quartz.SchedulerException;

import java.util.List;

/**
 * 定时任务服务接口
 */
public interface SysJobService {

    /**
     * 分页查询任务列表
     */
    PageResult<JobVO> listJob(JobQueryDTO dto);

    /**
     * 查询所有任务
     */
    List<JobVO> listAllJob();

    /**
     * 根据ID查询任务
     */
    JobVO getJobById(Long jobId);

    /**
     * 创建任务
     */
    void createJob(JobDTO dto) throws SchedulerException;

    /**
     * 更新任务
     */
    void updateJob(JobDTO dto) throws SchedulerException;

    /**
     * 删除任务
     */
    void deleteJob(Long jobId) throws SchedulerException;

    /**
     * 批量删除任务
     */
    void deleteJobByIds(Long[] jobIds) throws SchedulerException;

    /**
     * 暂停任务
     */
    void pauseJob(Long jobId) throws SchedulerException;

    /**
     * 恢复任务
     */
    void resumeJob(Long jobId) throws SchedulerException;

    /**
     * 立即执行一次
     */
    void runJob(Long jobId) throws SchedulerException;

    /**
     * 修改任务状态
     */
    void changeStatus(Long jobId, String status) throws SchedulerException;

    /**
     * 校验Cron表达式
     */
    boolean checkCronExpression(String cronExpression);
}
