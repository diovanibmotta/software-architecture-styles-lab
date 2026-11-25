package com.github.diovanibmotta.servicebased.shared;

import jakarta.persistence.*;

@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    public enum Status { PENDING, PAID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long productId;
    private int quantity;
    private long totalCents;
    @Enumerated(EnumType.STRING)
    private Status status;

    protected CustomerOrder() {
    }

    public CustomerOrder(Long productId, int quantity, long totalCents) {
        this.productId = productId;
        this.quantity = quantity;
        this.totalCents = totalCents;
        this.status = Status.PENDING;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public long getTotalCents() { return totalCents; }
    public Status getStatus() { return status; }
}
