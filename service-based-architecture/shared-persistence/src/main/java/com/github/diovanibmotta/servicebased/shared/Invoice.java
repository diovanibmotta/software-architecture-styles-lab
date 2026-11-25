package com.github.diovanibmotta.servicebased.shared;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long orderId;
    private long amountCents;
    private Instant paidAt;

    protected Invoice() {
    }

    public Invoice(Long orderId, long amountCents, Instant paidAt) {
        this.orderId = orderId;
        this.amountCents = amountCents;
        this.paidAt = paidAt;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public long getAmountCents() { return amountCents; }
    public Instant getPaidAt() { return paidAt; }
}
