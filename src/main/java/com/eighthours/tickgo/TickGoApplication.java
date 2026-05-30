package com.eighthours.tickgo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.eighthours.tickgo.mapper")
public class TickGoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TickGoApplication.class, args);
    }

}
