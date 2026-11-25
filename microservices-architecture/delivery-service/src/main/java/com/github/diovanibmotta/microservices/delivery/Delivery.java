package com.github.diovanibmotta.microservices.delivery;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("deliveries")
public record Delivery(@Id Long id, long orderId, String address, Status status, String courier) {

    public enum Status { ASSIGNED, NO_COURIER, DELIVERED }

    Delivery withStatus(Status newStatus) {
        return new Delivery(id, orderId, address, newStatus, courier);
    }
}
