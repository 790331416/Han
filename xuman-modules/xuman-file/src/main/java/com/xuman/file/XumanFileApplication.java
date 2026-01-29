package com.xuman.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 文件服务启动类
 */
@EnableDiscoveryClient
@SpringBootApplication
public class XumanFileApplication {
    public static void main(String[] args) {
        SpringApplication.run(XumanFileApplication.class, args);
    }
}
