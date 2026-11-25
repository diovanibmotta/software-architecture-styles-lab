package com.github.diovanibmotta.microservices.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Delivers outbox messages to the other services, at-least-once, in order, until each gets a 2xx. A down service just
 * delays its messages; the receivers are idempotent. Shortcut: a single relay instance polls (no locking), so running
 * two order-service replicas would need {@code SELECT ... FOR UPDATE SKIP LOCKED} or a broker/CDC (see docs/04).
 */
@Component
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final OutboxPayloads payloads;
    private final RestClient delivery;
    private final RestClient payment;

    OutboxRelay(OutboxRepository outbox, OutboxPayloads payloads, @Value("${delivery.url}") String deliveryUrl,
                @Value("${payment.url}") String paymentUrl, @Value("${resilience.timeout-ms}") long timeoutMs) {
        this.outbox = outbox;
        this.payloads = payloads;
        this.delivery = client(deliveryUrl, timeoutMs);
        this.payment = client(paymentUrl, timeoutMs);
    }

    private static RestClient client(String url, long timeoutMs) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build());
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    @Scheduled(fixedDelayString = "${outbox.interval-ms}")
    void relay() {
        for (OutboxMessage message : outbox.findBySentFalseOrderById()) {
            try {
                send(message);
                outbox.save(message.markSent());
            } catch (RuntimeException e) {
                log.warn("outbox {} {} order {} will be retried: {}", message.id(), message.kind(), message.orderId(), e.getMessage());
                outbox.save(message.markFailed());
            }
        }
    }

    private void send(OutboxMessage message) {
        switch (message.kind()) {
            case OutboxMessage.DISPATCH -> delivery.post().uri("/deliveries").contentType(MediaType.APPLICATION_JSON)
                    .body(message.payload()).retrieve().toBodilessEntity();
            case OutboxMessage.REFUND -> payment.post().uri("/payments/{id}/refund", payloads.paymentId(message))
                    .retrieve().toBodilessEntity();
            default -> throw new IllegalStateException("unknown outbox kind " + message.kind());
        }
    }
}
