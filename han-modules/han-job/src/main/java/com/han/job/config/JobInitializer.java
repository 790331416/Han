package com.han.job.config;

import com.han.job.domain.entity.SysJob;
import com.han.job.mapper.SysJobMapper;
import com.han.job.util.QuartzJobUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 任务初始化器
 * 应用启动时将数据库中的任务加载到Quartz调度器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobInitializer implements CommandLineRunner {

    private final SysJobMapper jobMapper;
    private final Scheduler scheduler;

    @Override
    public void run(String... args) {
        log.info("开始初始化定时任务...");
        
        try {
            // 清空调度器中的所有任务
            scheduler.clear();
            
            // 查询所有任务
            List<SysJob> jobs = jobMapper.selectList(null);
            
            for (SysJob job : jobs) {
                try {
                    QuartzJobUtils.createScheduleJob(scheduler, job);
                    log.info("初始化任务成功: {} ({})", job.getJobName(), job.getJobGroup());
                } catch (SchedulerException e) {
                    log.error("初始化任务失败: {} - {}", job.getJobName(), e.getMessage());
                }
            }
            
            log.info("定时任务初始化完成，共加载 {} 个任务", jobs.size());
            
        } catch (SchedulerException e) {
            log.error("清空调度器失败", e);
        }
    }
}
