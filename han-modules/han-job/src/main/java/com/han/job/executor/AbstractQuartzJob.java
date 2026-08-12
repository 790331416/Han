package com.han.job.executor;

import com.han.job.context.TraceContext;
import com.han.job.domain.po.SysJobLogPo;
import com.han.job.domain.po.SysJobPo;
import com.han.job.mapper.SysJobLogMapper;
import com.han.job.service.impl.JobHandlerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;

/**
 * 任务执行抽象类。
 *
 * <p>负责创建 TraceId、执行目标方法并持久化执行日志。</p>
 *
 * <p>本类是抽象类，不会被组件扫描注册成 Bean；依赖由 Spring 的
 * {@code SpringBeanJobFactory} 在 Quartz 每次创建 Job 实例时自动装配。</p>
 */
@Slf4j
public abstract class AbstractQuartzJob implements org.quartz.Job {

    private static final int EXCEPTION_INFO_MAX_LENGTH = 8000;

    private static SysJobLogMapper jobLogMapper;
    private static JobHandlerRegistry jobHandlerRegistry;

    @Autowired
    public void setJobLogMapper(SysJobLogMapper mapper) {
        AbstractQuartzJob.jobLogMapper = mapper;
    }

    @Autowired
    public void setJobHandlerRegistry(JobHandlerRegistry registry) {
        AbstractQuartzJob.jobHandlerRegistry = registry;
    }

    @Override
    public void execute(JobExecutionContext context) {
        SysJobPo job = (SysJobPo) context.getMergedJobDataMap().get("JOB_PROPERTIES");

        String traceId = TraceContext.generateTraceId();
        TraceContext.setTraceId(traceId);
        TraceContext.setJobId(job.getJobId());

        SysJobLogPo jobLog = new SysJobLogPo();
        jobLog.setTenantId(job.getTenantId());
        jobLog.setJobName(job.getJobName());
        jobLog.setJobGroup(job.getJobGroup());
        jobLog.setInvokeTarget(job.getInvokeTarget());
        jobLog.setTraceId(traceId);
        jobLog.setStartTime(LocalDateTime.now());

        log.info("开始执行任务: {}, traceId={}", job.getJobName(), traceId);

        try {
            doExecute(context, job);
            jobLog.setStatus("0");
            jobLog.setJobMessage("执行成功");
            log.info("任务执行成功: {}, traceId={}", job.getJobName(), traceId);
        } catch (Exception e) {
            log.error("任务执行失败: {}, traceId={}", job.getJobName(), traceId, e);
            jobLog.setStatus("1");
            jobLog.setJobMessage("执行失败: " + e.getMessage());
            jobLog.setExceptionInfo(truncateException(e));
        } finally {
            jobLog.setStopTime(LocalDateTime.now());
            saveJobLog(jobLog);
            TraceContext.clear();
        }
    }

    /**
     * 执行任务，由子类实现具体逻辑。
     */
    protected abstract void doExecute(JobExecutionContext context, SysJobPo job) throws Exception;

    /**
     * 调用任务目标方法。
     *
     * <p>调用目标必须先通过 {@link JobHandlerRegistry} 的白名单解析：只有标注了
     * {@code @JobHandler} / {@code @JobHandlerMethod} 并注册进容器的方法才允许执行。
     * 历史脏数据或绕过管理端直接写库的调用目标会在这里被拒绝，不会落到反射调用。</p>
     */
    protected void invokeMethod(SysJobPo job) throws Exception {
        if (jobHandlerRegistry == null) {
            throw new IllegalStateException("任务处理器注册表未初始化，拒绝执行调用目标: " + job.getInvokeTarget());
        }

        JobHandlerRegistry.JobInvocation invocation = jobHandlerRegistry.resolve(job.getInvokeTarget());
        try {
            invocation.invoke();
        } catch (InvocationTargetException e) {
            // 反射包装层会掩盖真实异常，日志与 sys_job_log 需要看到业务侧的原始堆栈
            Throwable cause = e.getTargetException();
            if (cause instanceof Exception businessException) {
                throw businessException;
            }
            throw e;
        }
    }

    private void saveJobLog(SysJobLogPo jobLog) {
        try {
            if (jobLogMapper != null) {
                jobLogMapper.insert(jobLog);
            }
        } catch (Exception e) {
            log.error("保存任务日志失败", e);
        }
    }

    private String truncateException(Exception e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        String stackTrace = writer.toString();
        // sys_job_log.exception_info 是 TEXT 列，放得下完整堆栈；留出余量避免个别超长堆栈撑爆行
        if (stackTrace.length() > EXCEPTION_INFO_MAX_LENGTH) {
            return stackTrace.substring(0, EXCEPTION_INFO_MAX_LENGTH);
        }
        return stackTrace;
    }
}
