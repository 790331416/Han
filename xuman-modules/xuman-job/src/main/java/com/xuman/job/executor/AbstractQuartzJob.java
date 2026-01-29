package com.xuman.job.executor;

import com.xuman.job.context.TraceContext;
import com.xuman.job.domain.entity.SysJob;
import com.xuman.job.domain.entity.SysJobLog;
import com.xuman.job.mapper.SysJobLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 任务执行抽象类
 * JobFlow 改造：支持 TraceId 全链路追踪
 */
@Slf4j
@Component
public abstract class AbstractQuartzJob implements org.quartz.Job, ApplicationContextAware {

    private static ApplicationContext applicationContext;
    private static SysJobLogMapper jobLogMapper;

    @Autowired
    public void setJobLogMapper(SysJobLogMapper mapper) {
        AbstractQuartzJob.jobLogMapper = mapper;
    }

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        AbstractQuartzJob.applicationContext = context;
    }

    @Override
    public void execute(JobExecutionContext context) {
        SysJob job = (SysJob) context.getMergedJobDataMap().get("JOB_PROPERTIES");
        
        // JobFlow 特性：生成 TraceId 并设置到 MDC
        String traceId = TraceContext.generateTraceId();
        TraceContext.setTraceId(traceId);
        TraceContext.setJobId(job.getJobId());
        
        SysJobLog jobLog = new SysJobLog();
        jobLog.setJobName(job.getJobName());
        jobLog.setJobGroup(job.getJobGroup());
        jobLog.setInvokeTarget(job.getInvokeTarget());
        jobLog.setTraceId(traceId);  // 设置 TraceId
        jobLog.setStartTime(LocalDateTime.now());
        
        log.info("开始执行任务: {}, traceId={}", job.getJobName(), traceId);

        try {
            // 执行任务
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
            
            // 清除 MDC 上下文
            TraceContext.clear();
        }
    }

    /**
     * 执行任务(子类实现)
     */
    protected abstract void doExecute(JobExecutionContext context, SysJob job) throws Exception;

    /**
     * 调用目标方法
     */
    protected void invokeMethod(SysJob job) throws Exception {
        String invokeTarget = job.getInvokeTarget();
        
        // 解析Bean名称和方法名
        // 格式: beanName.methodName 或 beanName.methodName(params)
        String beanName;
        String methodName;
        String params = null;
        
        int methodIndex = invokeTarget.indexOf('.');
        if (methodIndex <= 0) {
            throw new RuntimeException("调用目标格式错误: " + invokeTarget);
        }
        
        beanName = invokeTarget.substring(0, methodIndex);
        String methodPart = invokeTarget.substring(methodIndex + 1);
        
        int paramIndex = methodPart.indexOf('(');
        if (paramIndex > 0) {
            methodName = methodPart.substring(0, paramIndex);
            params = methodPart.substring(paramIndex + 1, methodPart.length() - 1);
        } else {
            methodName = methodPart;
        }
        
        // 获取Bean并调用方法
        Object bean = applicationContext.getBean(beanName);
        Method method;
        
        if (params != null && !params.isEmpty()) {
            method = bean.getClass().getDeclaredMethod(methodName, String.class);
            method.invoke(bean, params);
        } else {
            method = bean.getClass().getDeclaredMethod(methodName);
            method.invoke(bean);
        }
    }

    private void saveJobLog(SysJobLog jobLog) {
        try {
            if (jobLogMapper != null) {
                jobLogMapper.insert(jobLog);
            }
        } catch (Exception e) {
            log.error("保存任务日志失败", e);
        }
    }

    private String truncateException(Exception e) {
        String message = e.toString();
        if (message.length() > 2000) {
            return message.substring(0, 2000);
        }
        return message;
    }
}
