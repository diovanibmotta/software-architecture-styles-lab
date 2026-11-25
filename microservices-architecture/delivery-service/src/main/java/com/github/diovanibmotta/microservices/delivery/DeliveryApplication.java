package com.github.diovanibmotta.microservices.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

/** Bounded context "Delivery": courier assignment and drop-off. Reports outcomes back to orders by callback. */
@SpringBootApplication
@PropertySource("classpath:delivery.properties")
public class DeliveryApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeliveryApplication.class, args);
    }
}
