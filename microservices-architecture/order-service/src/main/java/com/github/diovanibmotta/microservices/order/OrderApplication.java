package com.github.diovanibmotta.microservices.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bounded context "Order": owns the order lifecycle and runs the saga that spans catalog, payment and delivery. */
@SpringBootApplication
@EnableScheduling
@PropertySource("classpath:order.properties")
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
