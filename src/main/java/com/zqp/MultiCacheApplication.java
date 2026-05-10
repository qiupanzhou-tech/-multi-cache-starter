package com.zqp;

import com.zqp.annotation.EnableMultiCache;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableMultiCache
@MapperScan("com.zqp.mapper")
public class MultiCacheApplication {
    public static void main(String[] args) {
        SpringApplication.run(MultiCacheApplication.class, args);
    }
}