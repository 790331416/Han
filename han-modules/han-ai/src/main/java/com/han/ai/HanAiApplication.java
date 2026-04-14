package com.han.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * AI service bootstrap.
 */
@SpringBootApplication(scanBasePackages = "com.han")
@EnableDiscoveryClient
public class HanAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanAiApplication.class, args);
        System.out.println("==== han AI 服务启动成功 ====");
    }
}
