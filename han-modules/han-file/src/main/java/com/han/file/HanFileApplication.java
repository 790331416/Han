package com.han.file;

import com.han.file.config.FileProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 文件服务启动类
 */
@EnableDiscoveryClient
@EnableConfigurationProperties(FileProperties.class)
@SpringBootApplication(scanBasePackages = "com.han")
public class HanFileApplication {
    public static void main(String[] args) {
        SpringApplication.run(HanFileApplication.class, args);
    }
}
