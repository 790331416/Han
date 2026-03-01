package com.han.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 文件服务启动类
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.han")
public class HanFileApplication {
    public static void main(String[] args) {
        SpringApplication.run(HanFileApplication.class, args);
    }
}
