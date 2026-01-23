package com.xuman.job.controller;

import com.xuman.common.core.domain.PageResult;
import com.xuman.common.core.domain.R;
import com.xuman.job.domain.dto.JobLogQueryDTO;
import com.xuman.job.domain.vo.JobLogVO;
import com.xuman.job.service.SysJobLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 任务日志 Controller
 */
@RestController
@RequestMapping("/job/log")
@RequiredArgsConstructor
public class SysJobLogController {

    private final SysJobLogService jobLogService;

    /**
     * 分页查询任务日志
     */
    @GetMapping("/list")
    public R<PageResult<JobLogVO>> list(JobLogQueryDTO dto) {
        return R.ok(jobLogService.listJobLog(dto));
    }

    /**
     * 获取日志详情
     */
    @GetMapping("/{jobLogId}")
    public R<JobLogVO> getInfo(@PathVariable Long jobLogId) {
        return R.ok(jobLogService.getJobLogById(jobLogId));
    }

    /**
     * 删除日志
     */
    @DeleteMapping("/{jobLogIds}")
    public R<Void> remove(@PathVariable Long[] jobLogIds) {
        jobLogService.deleteJobLogByIds(jobLogIds);
        return R.ok();
    }

    /**
     * 清空日志
     */
    @DeleteMapping("/clean")
    public R<Void> clean() {
        jobLogService.cleanJobLog();
        return R.ok();
    }
}
