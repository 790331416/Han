package com.han.ai;

import com.han.common.web.http.EnableHttpClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * AI 服务启动入口。
 */
@SpringBootApplication(scanBasePackages = "com.han")
@EnableDiscoveryClient
@EnableHttpClients
public class HanAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanAiApplication.class, args);
        System.out.println("==== han AI 服务启动成功 ====");
    }
}
