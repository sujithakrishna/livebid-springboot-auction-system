package com.sujitha.livebidspringboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LivebidSpringbootApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                LivebidSpringbootApplication.class,
                args
        );
    }
}