package com.xuman.tenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 租户管理服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
public class XumanTenantApplication {

    public static void main(String[] args) {
        SpringApplication.run(XumanTenantApplication.class, args);
        System.out.println("==== XuMan Tenant 租户管理服务启动成功 ====");
    }
}
