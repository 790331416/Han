package com.xuman.job.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 示例任务Handler
 * 
 * 使用方式:
 * 1. 在数据库或管理界面创建任务
 * 2. 调用目标填写: sampleTask.execute 或 sampleTask.executeWithParam(参数)
 * 3. 配置Cron表达式
 */
@Slf4j
@Component("sampleTask")
public class SampleTaskHandler {

    /**
     * 简单任务示例(无参)
     * 调用目标: sampleTask.execute
     */
    public void execute() {
        log.info("执行简单任务...");
        // 业务逻辑
        log.info("简单任务执行成功");
    }

    /**
     * 带参数任务示例
     * 调用目标: sampleTask.executeWithParam(参数值)
     */
    public void executeWithParam(String param) {
        log.info("执行带参数任务, 参数: {}", param);
        // 业务逻辑
        log.info("带参数任务执行成功");
    }

    /**
     * 多个参数任务示例
     * 调用目标: sampleTask.executeMultiParams(参数1,参数2)
     * 注意: 多参数用逗号分隔
     */
    public void executeMultiParams(String params) {
        String[] paramArr = params.split(",");
        log.info("执行多参数任务, 参数数量: {}", paramArr.length);
        for (int i = 0; i < paramArr.length; i++) {
            log.info("参数{}: {}", i + 1, paramArr[i].trim());
        }
        log.info("多参数任务执行成功");
    }

    /**
     * 数据同步任务示例
     * 调用目标: sampleTask.syncData
     */
    public void syncData() {
        log.info("开始数据同步任务...");
        try {
            // 模拟数据同步
            Thread.sleep(1000);
            log.info("数据同步完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("数据同步被中断", e);
        }
    }

    /**
     * 清理任务示例
     * 调用目标: sampleTask.cleanData
     */
    public void cleanData() {
        log.info("开始执行数据清理任务...");
        // 清理过期数据、临时文件等
        log.info("数据清理完成");
    }

    /**
     * 报表生成任务示例
     * 调用目标: sampleTask.generateReport(daily) 或 sampleTask.generateReport(weekly)
     */
    public void generateReport(String reportType) {
        log.info("开始生成{}报表...", reportType);
        // 生成报表逻辑
        log.info("{}报表生成完成", reportType);
    }
}
