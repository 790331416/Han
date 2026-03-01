package com.han.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 定时任务服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.han")
@EnableDiscoveryClient
public class HanJobApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanJobApplication.class, args);
        System.out.println("==== han Job 定时任务服务启动成功 ====");
    }
}
