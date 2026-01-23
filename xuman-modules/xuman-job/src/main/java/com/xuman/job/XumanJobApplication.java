package com.xuman.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 定时任务服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
public class XumanJobApplication {

    public static void main(String[] args) {
        SpringApplication.run(XumanJobApplication.class, args);
        System.out.println("==== XuMan Job 定时任务服务启动成功 ====");
    }
}
