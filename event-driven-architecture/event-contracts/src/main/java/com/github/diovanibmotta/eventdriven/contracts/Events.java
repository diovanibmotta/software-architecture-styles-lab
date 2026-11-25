package com.github.diovanibmotta.eventdriven.contracts;

import java.util.Map;

/**
 * The event catalogue: one record per fact that happened, named in the past tense. Each event type has its own
 * topic (JMS pub/sub address). Producers do not know who listens; this is the only contract services share.
 */
public final class Events {

    public record OrderPlaced(String orderId, String customerId, String sku, int quantity, long amountCents) {}

    public record StockReserved(String orderId, String customerId, long amountCents) {}

    public record StockRejected(String orderId, String customerId, String reason) {}

    public record PaymentApproved(String orderId, String customerId, long amountCents) {}

    public record PaymentDeclined(String orderId, String customerId, String reason) {}

    public static final String ORDER_PLACED = "orders.placed";
    public static final String STOCK_RESERVED = "inventory.stock-reserved";
    public static final String STOCK_REJECTED = "inventory.stock-rejected";
    public static final String PAYMENT_APPROVED = "payments.approved";
    public static final String PAYMENT_DECLINED = "payments.declined";

    /** Event type -> topic. Also used as the JMS type id so consumers can deserialize without sharing class names. */
    static final Map<Class<?>, String> TOPICS = Map.of(
            OrderPlaced.class, ORDER_PLACED,
            StockReserved.class, STOCK_RESERVED,
            StockRejected.class, STOCK_REJECTED,
            PaymentApproved.class, PAYMENT_APPROVED,
            PaymentDeclined.class, PAYMENT_DECLINED);

    private Events() {}
}
