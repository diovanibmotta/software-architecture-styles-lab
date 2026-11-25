package com.github.diovanibmotta.eventdriven.payment;

import com.github.diovanibmotta.eventdriven.contracts.EventPublisher;
import com.github.diovanibmotta.eventdriven.contracts.Events;
import com.github.diovanibmotta.eventdriven.contracts.Events.PaymentApproved;
import com.github.diovanibmotta.eventdriven.contracts.Events.PaymentDeclined;
import com.github.diovanibmotta.eventdriven.contracts.Events.StockReserved;
import com.github.diovanibmotta.eventdriven.contracts.MessagingConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake payment gateway: charges once stock is reserved. Approves up to a limit, declines above it
 * (stands in for "card refused"). Idempotent per order: a redelivery republishes the same decision.
 */
@Service
public class PaymentService {

    public record Payment(String orderId, boolean approved, String reason) {}

    private final Map<String, Payment> payments = new ConcurrentHashMap<>();
    private final EventPublisher events;
    private final long limitCents;

    PaymentService(EventPublisher events, @Value("${payment.limit-cents:1000000}") long limitCents) {
        this.events = events;
        this.limitCents = limitCents;
    }

    public Optional<Payment> find(String orderId) {
        return Optional.ofNullable(payments.get(orderId));
    }

    @JmsListener(destination = Events.STOCK_RESERVED, subscription = "payment-stock-reserved",
            containerFactory = MessagingConfig.TOPIC_FACTORY)
    void on(StockReserved e) {
        Payment p = payments.computeIfAbsent(e.orderId(), id -> e.amountCents() <= limitCents
                ? new Payment(id, true, null)
                : new Payment(id, false, "amount " + e.amountCents() + " above limit " + limitCents));
        events.publish(p.approved()
                ? new PaymentApproved(e.orderId(), e.customerId(), e.amountCents())
                : new PaymentDeclined(e.orderId(), e.customerId(), p.reason()));
    }
}
