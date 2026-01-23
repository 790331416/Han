package com.xuman.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
public class XumanGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(XumanGatewayApplication.class, args);
        System.out.println("==== XuMan Gateway 启动成功 ====");
    }
}
