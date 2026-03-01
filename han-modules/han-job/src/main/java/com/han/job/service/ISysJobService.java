package com.han.job.service;

import com.han.common.core.domain.PageResult;
import com.han.common.web.service.IBaseService;
import com.han.job.domain.dto.JobDTO;
import com.han.job.domain.query.JobQuery;
import com.han.job.domain.vo.JobVO;
import org.quartz.SchedulerException;

import java.util.List;

/**
 * 定时任务服务接口
 */
public interface ISysJobService extends IBaseService<JobQuery, JobDTO> {

    /**
     * 分页查询任务
     */
    PageResult<JobDTO> listJob(JobQuery query);

    /**
     * 根据ID查询任务
     */
    JobDTO getJobById(Long jobId);

    /**
     * 查询所有任务
     */
    List<JobVO> listAllJob();

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
