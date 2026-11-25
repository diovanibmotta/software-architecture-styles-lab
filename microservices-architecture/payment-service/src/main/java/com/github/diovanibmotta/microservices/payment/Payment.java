package com.github.diovanibmotta.microservices.payment;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("payments")
public record Payment(@Id Long id, long orderId, long amountCents, Status status) {

    public enum Status { AUTHORIZED, DECLINED, REFUNDED }

    Payment withStatus(Status newStatus) {
        return new Payment(id, orderId, amountCents, newStatus);
    }
}
