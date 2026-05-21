package com.han.aivideo;

import com.han.common.web.http.EnableHttpClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * AI short-drama service bootstrap.
 */
@SpringBootApplication(scanBasePackages = "com.han")
@EnableDiscoveryClient
@EnableHttpClients
public class HanAivideoApplication {

    public static void main(String[] args) {
        SpringApplication.run(HanAivideoApplication.class, args);
        System.out.println("==== han AI 短剧服务启动成功 ====");
    }
}
