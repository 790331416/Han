package com.xuman.job.executor;

import com.xuman.job.domain.entity.SysJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

/**
 * 禁止并发执行的任务
 */
@DisallowConcurrentExecution
public class QuartzDisallowConcurrentExecution extends AbstractQuartzJob {

    @Override
    protected void doExecute(JobExecutionContext context, SysJob job) throws Exception {
        invokeMethod(job);
    }
}
