package com.han.job.controller;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.job.domain.query.JobLogQuery;
import com.han.job.domain.vo.JobLogVO;
import com.han.job.service.ISysJobLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 调度日志控制器。
 */
@RestController
@RequestMapping("/job/log")
@RequiredArgsConstructor
public class SysJobLogController {

    private final ISysJobLogService jobLogService;

    /**
     * 分页查询调度日志。
     *
     * @param query 查询条件
     * @return 日志分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('job:log:list')")
    public R<PageResult<JobLogVO>> list(JobLogQuery query) {
        return R.ok(jobLogService.listJobLog(query));
    }

    /**
     * 查询日志详情。
     *
     * @param jobLogId 日志ID
     * @return 日志详情
     */
    @GetMapping("/{jobLogId}")
    @PreAuthorize("@ss.hasAuthority('job:log:list')")
    public R<JobLogVO> getInfo(@PathVariable Long jobLogId) {
        return R.ok(jobLogService.getJobLogById(jobLogId));
    }

    /**
     * 删除日志。
     *
     * @param jobLogIds 日志ID数组
     * @return 处理结果
     */
    @PostMapping("/remove/{jobLogIds}")
    @PreAuthorize("@ss.hasAuthority('job:log:remove')")
    public R<Void> remove(@PathVariable Long[] jobLogIds) {
        jobLogService.deleteJobLogByIds(jobLogIds);
        return R.ok();
    }

    /**
     * 清空日志。
     *
     * @return 处理结果
     */
    @PostMapping("/clean")
    @PreAuthorize("@ss.hasAuthority('job:log:remove')")
    public R<Void> clean() {
        jobLogService.cleanJobLog();
        return R.ok();
    }
}
