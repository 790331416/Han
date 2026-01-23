package com.xuman.open;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 开放平台服务启动类
 * 提供OAuth2授权服务、SSO单点登录、第三方应用接入
 */
@SpringBootApplication
@EnableDiscoveryClient
public class XumanOpenApplication {

    public static void main(String[] args) {
        SpringApplication.run(XumanOpenApplication.class, args);
        System.out.println("==== XuMan Open 开放平台服务启动成功 ====");
    }
}
