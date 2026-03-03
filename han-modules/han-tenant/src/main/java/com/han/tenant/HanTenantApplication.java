package com.han.tenant;

import com.han.common.web.http.EnableHttpClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 租户管理服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.han")
@EnableDiscoveryClient
@EnableHttpClients
@EnableScheduling
public class HanTenantApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanTenantApplication.class, args);
        System.out.println("==== han Tenant 租户管理服务启动成功 ====");
    }
}
