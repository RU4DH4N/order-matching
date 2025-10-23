package ru4dh4n.ordermatching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class OrderMatchingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderMatchingApplication.class, args);
    }
}
