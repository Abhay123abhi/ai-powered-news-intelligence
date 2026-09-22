package com.example.news;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.validation.annotation.Validated;

@EnableCaching
@SpringBootApplication
@EnableFeignClients
@Validated
public class NewsAggregatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(NewsAggregatorApplication.class, args);
    }
}
