package com.han.monitor;

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
public class HanMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanMonitorApplication.class, args);
        System.out.println("==== han Monitor 启动成功 ====");
    }
}
