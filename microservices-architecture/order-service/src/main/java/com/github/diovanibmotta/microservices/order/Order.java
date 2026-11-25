package com.github.diovanibmotta.microservices.order;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("orders")
public record Order(@Id Long id, String customerId, long itemId, int quantity, String address, long totalCents,
                    Status status, String reason, Long paymentId, String courier) {

    /** PENDING -> PAID -> CONFIRMED -> DELIVERED, or REJECTED (nothing charged) / CANCELLED (charged, then refunded). */
    public enum Status { PENDING, PAID, CONFIRMED, DELIVERED, REJECTED, CANCELLED }

    Order with(Status newStatus, String newReason) {
        return new Order(id, customerId, itemId, quantity, address, totalCents, newStatus, newReason, paymentId, courier);
    }

    Order paid(long newPaymentId) {
        return new Order(id, customerId, itemId, quantity, address, totalCents, Status.PAID, null, newPaymentId, courier);
    }

    Order confirmed(String newCourier) {
        return new Order(id, customerId, itemId, quantity, address, totalCents, Status.CONFIRMED, null, paymentId, newCourier);
    }
}
