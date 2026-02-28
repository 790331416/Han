package com.han.job.executor;

import com.han.job.domain.entity.SysJob;
import org.quartz.JobExecutionContext;

/**
 * 允许并发执行的任务
 */
public class QuartzJobExecution extends AbstractQuartzJob {

    @Override
    protected void doExecute(JobExecutionContext context, SysJob job) throws Exception {
        invokeMethod(job);
    }
}
