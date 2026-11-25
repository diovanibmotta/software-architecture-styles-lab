package com.github.diovanibmotta.microservices.delivery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Tells the order-service what happened. Throws when it can't, so the caller answers 5xx and the sender retries. */
@Component
class OrderCallback {

    private final RestClient client;

    OrderCallback(@Value("${order.url}") String orderUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.client = RestClient.builder().baseUrl(orderUrl).requestFactory(factory).build();
    }

    void report(Delivery delivery) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", delivery.status().name());
        body.put("courier", delivery.courier());
        client.post().uri("/orders/{id}/delivery", delivery.orderId()).body(body).retrieve().toBodilessEntity();
    }
}
