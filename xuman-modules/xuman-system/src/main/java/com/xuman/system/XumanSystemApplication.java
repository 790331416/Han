package com.xuman.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 系统服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.xuman.system.mapper")
public class XumanSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(XumanSystemApplication.class, args);
        System.out.println("==== XuMan System 启动成功 ====");
    }
}
