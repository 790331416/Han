package com.xuman.monitor;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 监控中心启动类
 */
@SpringBootApplication
@EnableAdminServer
@EnableDiscoveryClient
public class XumanMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(XumanMonitorApplication.class, args);
        System.out.println("==== XuMan Monitor 启动成功 ====");
    }
}
