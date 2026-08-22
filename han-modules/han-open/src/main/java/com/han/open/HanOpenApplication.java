package com.han.open;

import com.han.common.web.http.EnableHttpClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 开放平台服务启动类
 * 提供OAuth2授权服务、SSO单点登录、第三方应用接入
 */
@SpringBootApplication(scanBasePackages = "com.han")
@EnableDiscoveryClient
@EnableHttpClients
public class HanOpenApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanOpenApplication.class, args);
        System.out.println("==== han Open 开放平台服务启动成功 ====");
    }
}
