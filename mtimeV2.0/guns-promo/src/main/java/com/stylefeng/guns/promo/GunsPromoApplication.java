package com.stylefeng.guns.promo;

import com.alibaba.dubbo.spring.boot.annotation.EnableDubboConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.stylefeng.guns"})
@EnableDubboConfiguration
public class GunsPromoApplication {

    public static void main(String[] args) {
        SpringApplication.run(GunsPromoApplication.class, args);
    }
}
