package com.han.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 系统服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.han")
@EnableDiscoveryClient
@MapperScan("com.han.system.mapper")
public class HanSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanSystemApplication.class, args);
        System.out.println("==== han System 启动成功 ====");
    }
}
