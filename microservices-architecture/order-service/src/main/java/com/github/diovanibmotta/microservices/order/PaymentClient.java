package com.github.diovanibmotta.microservices.order;

import java.util.Map;

public class PaymentClient {

    public record Payment(long id, String status) {
        public boolean authorized() {
            return "AUTHORIZED".equals(status);
        }
    }

    private final Downstream downstream;

    public PaymentClient(Downstream downstream) {
        this.downstream = downstream;
    }

    /** Idempotent on the payment side (keyed by order id), so a retry after a timeout cannot charge twice. */
    public Payment authorize(long orderId, long amountCents) {
        return downstream.call(c -> c.post().uri("/payments")
                .body(Map.of("orderId", orderId, "amountCents", amountCents)).retrieve().body(Payment.class));
    }
}
