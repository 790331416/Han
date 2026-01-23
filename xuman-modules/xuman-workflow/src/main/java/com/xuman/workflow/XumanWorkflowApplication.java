package com.xuman.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 工作流服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
public class XumanWorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(XumanWorkflowApplication.class, args);
        System.out.println("==== XuMan Workflow 工作流服务启动成功 ====");
    }
}
