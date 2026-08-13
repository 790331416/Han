package com.han.tenant;

import com.han.common.web.http.EnableHttpClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 租户管理服务启动类
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.han")
@EnableDiscoveryClient
@EnableHttpClients
@EnableScheduling
public class HanTenantApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanTenantApplication.class, args);
        log.info("==== han Tenant 租户管理服务启动成功 ====");
    }
}
