package com.github.diovanibmotta.microservices.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ResilienceConfig {

    @Bean
    Downstream.Settings settings(@Value("${resilience.timeout-ms}") long timeoutMs, @Value("${resilience.window}") int window,
                                 @Value("${resilience.min-calls}") int minCalls,
                                 @Value("${resilience.failure-rate}") float failureRate,
                                 @Value("${resilience.open-ms}") long openMs) {
        return new Downstream.Settings(timeoutMs, window, minCalls, failureRate, openMs);
    }

    @Bean
    CatalogClient catalogClient(@Value("${catalog.url}") String url, Downstream.Settings settings) {
        return new CatalogClient(new Downstream("catalog", url, settings));
    }

    @Bean
    PaymentClient paymentClient(@Value("${payment.url}") String url, Downstream.Settings settings) {
        return new PaymentClient(new Downstream("payment", url, settings));
    }
}
