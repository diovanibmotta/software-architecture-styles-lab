package com.github.diovanibmotta.microservices.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

/** Bounded context "Payment": authorizes and refunds. Knows order ids only as opaque numbers, never the order itself. */
@SpringBootApplication
@PropertySource("classpath:payment.properties")
public class PaymentApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
