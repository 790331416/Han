package com.han.gen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 代码生成服务
 */
@SpringBootApplication(scanBasePackages = "com.han")
@EnableDiscoveryClient
public class HanGenApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanGenApplication.class, args);
    }
}
