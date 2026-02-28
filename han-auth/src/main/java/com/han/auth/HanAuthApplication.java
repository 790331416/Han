package com.han.auth;

import com.han.common.web.http.EnableHttpClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 认证中心启动类
 */
@SpringBootApplication(scanBasePackages = "com.han", exclude = {DataSourceAutoConfiguration.class})
@EnableDiscoveryClient
@EnableHttpClients
public class HanAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanAuthApplication.class, args);
        System.out.println("==== han Auth 启动成功 ====");
    }
}
