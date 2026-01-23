package com.xuman.job.util;

import com.xuman.job.domain.entity.SysJob;
import com.xuman.job.executor.QuartzDisallowConcurrentExecution;
import com.xuman.job.executor.QuartzJobExecution;
import org.quartz.*;

/**
 * Quartz 任务工具类
 */
public class QuartzJobUtils {

    /**
     * 获取任务Key
     */
    public static JobKey getJobKey(SysJob job) {
        return JobKey.jobKey(String.valueOf(job.getJobId()), job.getJobGroup());
    }

    /**
     * 获取触发器Key
     */
    public static TriggerKey getTriggerKey(SysJob job) {
        return TriggerKey.triggerKey(String.valueOf(job.getJobId()), job.getJobGroup());
    }

    /**
     * 创建定时任务
     */
    public static void createScheduleJob(Scheduler scheduler, SysJob job) throws SchedulerException {
        // 选择Job类(是否允许并发)
        Class<? extends Job> jobClass = "0".equals(job.getConcurrent()) 
                ? QuartzJobExecution.class 
                : QuartzDisallowConcurrentExecution.class;

        // 构建JobDetail
        JobDetail jobDetail = JobBuilder.newJob(jobClass)
                .withIdentity(getJobKey(job))
                .withDescription(job.getRemark())
                .build();

        // 传递任务参数
        jobDetail.getJobDataMap().put("JOB_PROPERTIES", job);

        // 构建Cron触发器
        CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder.cronSchedule(job.getCronExpression());
        cronScheduleBuilder = handleMisfirePolicy(cronScheduleBuilder, job.getMisfirePolicy());

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(getTriggerKey(job))
                .withSchedule(cronScheduleBuilder)
                .build();

        // 判断任务是否存在
        if (scheduler.checkExists(getJobKey(job))) {
            scheduler.deleteJob(getJobKey(job));
        }

        scheduler.scheduleJob(jobDetail, trigger);

        // 如果任务状态是暂停，则暂停任务
        if ("1".equals(job.getStatus())) {
            scheduler.pauseJob(getJobKey(job));
        }
    }

    /**
     * 更新定时任务
     */
    public static void updateScheduleJob(Scheduler scheduler, SysJob job) throws SchedulerException {
        TriggerKey triggerKey = getTriggerKey(job);
        
        // 构建Cron触发器
        CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder.cronSchedule(job.getCronExpression());
        cronScheduleBuilder = handleMisfirePolicy(cronScheduleBuilder, job.getMisfirePolicy());

        CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey);
        if (trigger == null) {
            createScheduleJob(scheduler, job);
            return;
        }

        trigger = trigger.getTriggerBuilder()
                .withIdentity(triggerKey)
                .withSchedule(cronScheduleBuilder)
                .build();

        // 更新JobDataMap
        JobKey jobKey = getJobKey(job);
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        jobDetail.getJobDataMap().put("JOB_PROPERTIES", job);

        scheduler.rescheduleJob(triggerKey, trigger);

        // 如果任务状态是暂停，则暂停任务
        if ("1".equals(job.getStatus())) {
            scheduler.pauseJob(getJobKey(job));
        }
    }

    /**
     * 删除定时任务
     */
    public static void deleteScheduleJob(Scheduler scheduler, SysJob job) throws SchedulerException {
        scheduler.deleteJob(getJobKey(job));
    }

    /**
     * 暂停任务
     */
    public static void pauseJob(Scheduler scheduler, SysJob job) throws SchedulerException {
        scheduler.pauseJob(getJobKey(job));
    }

    /**
     * 恢复任务
     */
    public static void resumeJob(Scheduler scheduler, SysJob job) throws SchedulerException {
        scheduler.resumeJob(getJobKey(job));
    }

    /**
     * 立即执行任务
     */
    public static void runJobNow(Scheduler scheduler, SysJob job) throws SchedulerException {
        JobDataMap dataMap = new JobDataMap();
        dataMap.put("JOB_PROPERTIES", job);
        scheduler.triggerJob(getJobKey(job), dataMap);
    }

    /**
     * 处理Misfire策略
     */
    private static CronScheduleBuilder handleMisfirePolicy(CronScheduleBuilder builder, String misfirePolicy) {
        return switch (misfirePolicy) {
            case "1" -> builder.withMisfireHandlingInstructionIgnoreMisfires();  // 立即执行
            case "2" -> builder.withMisfireHandlingInstructionFireAndProceed();  // 执行一次
            case "3" -> builder.withMisfireHandlingInstructionDoNothing();       // 放弃执行
            default -> builder.withMisfireHandlingInstructionDoNothing();
        };
    }
}
