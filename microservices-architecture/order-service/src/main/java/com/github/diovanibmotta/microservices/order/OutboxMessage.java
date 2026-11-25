package com.github.diovanibmotta.microservices.order;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("outbox")
public record OutboxMessage(@Id Long id, String kind, long orderId, String payload, boolean sent, int attempts) {

    /** DISPATCH -> delivery-service (start delivery); REFUND -> payment-service (compensation). */
    public static final String DISPATCH = "DISPATCH";
    public static final String REFUND = "REFUND";

    OutboxMessage markSent() {
        return new OutboxMessage(id, kind, orderId, payload, true, attempts + 1);
    }

    OutboxMessage markFailed() {
        return new OutboxMessage(id, kind, orderId, payload, false, attempts + 1);
    }
}
