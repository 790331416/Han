package com.xuman.job.controller;

import com.xuman.common.core.domain.PageResult;
import com.xuman.common.core.domain.R;
import com.xuman.job.domain.dto.JobDTO;
import com.xuman.job.domain.dto.JobQueryDTO;
import com.xuman.job.domain.vo.JobVO;
import com.xuman.job.service.SysJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 定时任务管理 Controller
 * 提供前端管理界面所需的 API 接口
 */
@Slf4j
@RestController
@RequestMapping("/job")
@RequiredArgsConstructor
public class SysJobController {

    private final SysJobService jobService;

    /**
     * 分页查询任务列表
     */
    @GetMapping("/list")
    public R<PageResult<JobVO>> list(JobQueryDTO dto) {
        return R.ok(jobService.listJob(dto));
    }

    /**
     * 查询所有任务(不分页)
     */
    @GetMapping("/listAll")
    public R<List<JobVO>> listAll() {
        return R.ok(jobService.listAllJob());
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{jobId}")
    public R<JobVO> getInfo(@PathVariable Long jobId) {
        return R.ok(jobService.getJobById(jobId));
    }

    /**
     * 新增任务
     */
    @PostMapping
    public R<Void> add(@Valid @RequestBody JobDTO dto) {
        if (!jobService.checkCronExpression(dto.getCronExpression())) {
            return R.fail("Cron表达式不正确");
        }
        try {
            jobService.createJob(dto);
            return R.ok();
        } catch (SchedulerException e) {
            log.error("创建任务失败", e);
            return R.fail("创建任务失败: " + e.getMessage());
        }
    }

    /**
     * 修改任务
     */
    @PutMapping
    public R<Void> edit(@Valid @RequestBody JobDTO dto) {
        if (!jobService.checkCronExpression(dto.getCronExpression())) {
            return R.fail("Cron表达式不正确");
        }
        try {
            jobService.updateJob(dto);
            return R.ok();
        } catch (SchedulerException e) {
            log.error("更新任务失败", e);
            return R.fail("更新任务失败: " + e.getMessage());
        }
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{jobIds}")
    public R<Void> remove(@PathVariable Long[] jobIds) {
        try {
            jobService.deleteJobByIds(jobIds);
            return R.ok();
        } catch (SchedulerException e) {
            log.error("删除任务失败", e);
            return R.fail("删除任务失败: " + e.getMessage());
        }
    }

    /**
     * 修改任务状态
     */
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody JobDTO dto) {
        try {
            jobService.changeStatus(dto.getJobId(), dto.getStatus());
            return R.ok();
        } catch (SchedulerException e) {
            log.error("修改任务状态失败", e);
            return R.fail("修改任务状态失败: " + e.getMessage());
        }
    }

    /**
     * 立即执行一次
     */
    @PutMapping("/run/{jobId}")
    public R<Void> run(@PathVariable Long jobId) {
        try {
            jobService.runJob(jobId);
            return R.ok();
        } catch (SchedulerException e) {
            log.error("执行任务失败", e);
            return R.fail("执行任务失败: " + e.getMessage());
        }
    }

    /**
     * 校验Cron表达式是否有效
     */
    @GetMapping("/checkCron")
    public R<Boolean> checkCron(@RequestParam String cronExpression) {
        return R.ok(jobService.checkCronExpression(cronExpression));
    }
}
