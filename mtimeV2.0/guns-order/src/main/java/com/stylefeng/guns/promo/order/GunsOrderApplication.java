package com.stylefeng.guns.promo.order;

import com.alibaba.dubbo.spring.boot.annotation.EnableDubboConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.stylefeng.guns"})
@EnableDubboConfiguration
@EnableScheduling
public class GunsOrderApplication {

    public static void main(String[] args) {
       SpringApplication.run(GunsOrderApplication.class, args);
    }
}
