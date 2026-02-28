package com.han.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 工作流服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
public class HanWorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanWorkflowApplication.class, args);
        System.out.println("==== han Workflow 工作流服务启动成功 ====");
    }
}
