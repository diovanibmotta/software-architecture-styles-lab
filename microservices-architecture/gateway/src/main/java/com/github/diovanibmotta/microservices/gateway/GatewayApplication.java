package com.github.diovanibmotta.microservices.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.PropertySource;

/** Single public entry point: routing only. No business logic and no data of its own. */
@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
@PropertySource("classpath:gateway.properties")
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
