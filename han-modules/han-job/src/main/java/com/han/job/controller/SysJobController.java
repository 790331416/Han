package com.han.job.controller;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.job.domain.dto.JobDTO;
import com.han.job.domain.query.JobQuery;
import com.han.job.domain.vo.JobHandlerVO;
import com.han.job.service.ISysJobService;
import com.han.job.service.impl.JobHandlerRegistry;
import lombok.RequiredArgsConstructor;
import org.quartz.SchedulerException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 定时任务管理控制器。
 */
@RestController
@RequestMapping("/job")
@RequiredArgsConstructor
public class SysJobController {

    private final ISysJobService jobService;
    private final JobHandlerRegistry jobHandlerRegistry;

    /**
     * 分页查询任务列表。
     *
     * @param query 查询条件
     * @return 任务分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('job:list')")
    public R<PageResult<JobDTO>> list(JobQuery query) {
        return R.ok(jobService.listJob(query));
    }

    /**
     * 查询任务详情。
     *
     * @param jobId 任务ID
     * @return 任务详情
     */
    @GetMapping("/{jobId}")
    @PreAuthorize("@ss.hasAuthority('job:list')")
    public R<JobDTO> getInfo(@PathVariable Long jobId) {
        return R.ok(jobService.getJobById(jobId));
    }

    /**
     * 查询全部可用处理器（@JobHandler Bean 的 @JobHandlerMethod 方法）。
     *
     * @return 处理器列表
     */
    @GetMapping("/handlers")
    @PreAuthorize("@ss.hasAuthority('job:list')")
    public R<List<JobHandlerVO>> handlers() {
        return R.ok(jobHandlerRegistry.listHandlers());
    }

    /**
     * 新增任务。
     *
     * @param dto 任务信息
     * @return 处理结果
     * @throws SchedulerException 调度异常
     */
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('job:add')")
    public R<Void> add(@RequestBody JobDTO dto) throws SchedulerException {
        jobService.createJob(dto);
        return R.ok();
    }

    /**
     * 修改任务。
     *
     * @param dto 任务信息
     * @return 处理结果
     * @throws SchedulerException 调度异常
     */
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('job:edit')")
    public R<Void> edit(@RequestBody JobDTO dto) throws SchedulerException {
        jobService.updateJob(dto);
        return R.ok();
    }

    /**
     * 删除单个任务。
     *
     * @param jobId 任务ID
     * @return 处理结果
     * @throws SchedulerException 调度异常
     */
    @PostMapping("/remove/{jobId}")
    @PreAuthorize("@ss.hasAuthority('job:remove')")
    public R<Void> remove(@PathVariable Long jobId) throws SchedulerException {
        jobService.deleteJob(jobId);
        return R.ok();
    }

    /**
     * 批量删除任务。
     *
     * @param jobIds 任务ID数组
     * @return 处理结果
     * @throws SchedulerException 调度异常
     */
    @PostMapping("/remove")
    @PreAuthorize("@ss.hasAuthority('job:remove')")
    public R<Void> removeBatch(@RequestBody Long[] jobIds) throws SchedulerException {
        jobService.deleteJobByIds(jobIds);
        return R.ok();
    }

    /**
     * 修改任务状态。
     *
     * @param jobId 任务ID
     * @param status 状态值
     * @return 处理结果
     * @throws SchedulerException 调度异常
     */
    @PostMapping("/changeStatus")
    @PreAuthorize("@ss.hasAuthority('job:edit')")
    public R<Void> changeStatus(@RequestParam("jobId") Long jobId, @RequestParam("status") String status)
            throws SchedulerException {
        jobService.changeStatus(jobId, status);
        return R.ok();
    }

    /**
     * 立即执行任务。
     *
     * @param jobId 任务ID
     * @return 处理结果
     * @throws SchedulerException 调度异常
     */
    @PostMapping("/run/{jobId}")
    @PreAuthorize("@ss.hasAuthority('job:edit')")
    public R<Void> run(@PathVariable Long jobId) throws SchedulerException {
        jobService.runJob(jobId);
        return R.ok();
    }

    /**
     * 校验 Cron 表达式。
     *
     * @param cronExpression Cron 表达式
     * @return 是否合法
     */
    @GetMapping("/checkCron")
    @PreAuthorize("@ss.hasAnyAuthority('job:add','job:edit')")
    public R<Boolean> checkCron(@RequestParam("cronExpression") String cronExpression) {
        return R.ok(jobService.checkCronExpression(cronExpression));
    }
}
