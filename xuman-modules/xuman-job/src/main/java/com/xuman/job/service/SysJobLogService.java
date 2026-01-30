package com.xuman.job.service;

import com.xuman.common.core.domain.PageResult;
import com.xuman.job.domain.query.JobLogQuery;
import com.xuman.job.domain.vo.JobLogVO;

/**
 * 任务日志服务接口
 */
public interface SysJobLogService {

    /**
     * 分页查询任务日志
     */
    PageResult<JobLogVO> listJobLog(JobLogQuery query);

    /**
     * 根据ID查询日志详情
     */
    JobLogVO getJobLogById(Long jobLogId);

    /**
     * 删除日志
     */
    void deleteJobLog(Long jobLogId);

    /**
     * 批量删除日志
     */
    void deleteJobLogByIds(Long[] jobLogIds);

    /**
     * 清空日志
     */
    void cleanJobLog();
}
