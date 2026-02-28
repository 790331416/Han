package com.han.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
public class HanGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanGatewayApplication.class, args);
        System.out.println("==== han Gateway 启动成功 ====");
    }
}
