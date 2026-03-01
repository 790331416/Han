package com.han.job.controller;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.job.domain.dto.JobDTO;
import com.han.job.domain.query.JobQuery;
import com.han.job.domain.vo.JobVO;
import com.han.job.service.ISysJobService;
import lombok.RequiredArgsConstructor;
import org.quartz.SchedulerException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 定时任务管理 Controller
 */
@RestController
@RequestMapping("/job")
@RequiredArgsConstructor
public class SysJobController {

    private final ISysJobService jobService;

    /**
     * 分页查询任务列表
     */
    @GetMapping("/list")
    public R<PageResult<JobDTO>> list(JobQuery query) {
        return R.ok(jobService.listJob(query));
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{jobId}")
    public R<JobDTO> getInfo(@PathVariable Long jobId) {
        return R.ok(jobService.getJobById(jobId));
    }

    /**
     * 获取所有可用任务处理器
     */
    @GetMapping("/handlers")
    public R<List<JobVO>> handlers() {
        return R.ok(jobService.listAllJob());
    }

    /**
     * 新增任务
     */
    @PostMapping
    public R<Void> add(@RequestBody JobDTO dto) throws SchedulerException {
        jobService.createJob(dto);
        return R.ok();
    }

    /**
     * 修改任务
     */
    @PostMapping("/edit")
    public R<Void> edit(@RequestBody JobDTO dto) throws SchedulerException {
        jobService.updateJob(dto);
        return R.ok();
    }

    /**
     * 删除任务
     */
    @PostMapping("/remove/{jobId}")
    public R<Void> remove(@PathVariable Long jobId) throws SchedulerException {
        jobService.deleteJob(jobId);
        return R.ok();
    }

    /**
     * 批量删除任务
     */
    @PostMapping("/remove")
    public R<Void> removeBatch(@RequestBody Long[] jobIds) throws SchedulerException {
        jobService.deleteJobByIds(jobIds);
        return R.ok();
    }

    /**
     * 修改任务状态
     */
    @PostMapping("/changeStatus")
    public R<Void> changeStatus(@RequestParam("jobId") Long jobId, @RequestParam("status") String status) throws SchedulerException {
        jobService.changeStatus(jobId, status);
        return R.ok();
    }

    /**
     * 立即执行一次
     */
    @PostMapping("/run/{jobId}")
    public R<Void> run(@PathVariable Long jobId) throws SchedulerException {
        jobService.runJob(jobId);
        return R.ok();
    }

    /**
     * 校验Cron表达式
     */
    @GetMapping("/checkCron")
    public R<Boolean> checkCron(@RequestParam("cronExpression") String cronExpression) {
        return R.ok(jobService.checkCronExpression(cronExpression));
    }
}
