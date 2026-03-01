package com.han.job.executor;

import com.han.job.domain.po.SysJobPo;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

/**
 * 禁止并发执行的任务
 */
@DisallowConcurrentExecution
public class QuartzDisallowConcurrentExecution extends AbstractQuartzJob {

    @Override
    protected void doExecute(JobExecutionContext context, SysJobPo job) throws Exception {
        invokeMethod(job);
    }
}
