package com.alipay.demo;

import com.alibaba.dubbo.spring.boot.annotation.EnableDubboConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.alipay.demo"})
@EnableDubboConfiguration
public class GunsAlipayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GunsAlipayApplication.class, args);
    }
}
