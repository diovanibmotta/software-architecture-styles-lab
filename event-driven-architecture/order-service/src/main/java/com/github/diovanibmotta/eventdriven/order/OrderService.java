package com.github.diovanibmotta.eventdriven.order;

import com.github.diovanibmotta.eventdriven.contracts.EventPublisher;
import com.github.diovanibmotta.eventdriven.contracts.Events;
import com.github.diovanibmotta.eventdriven.contracts.Events.OrderPlaced;
import com.github.diovanibmotta.eventdriven.contracts.Events.PaymentApproved;
import com.github.diovanibmotta.eventdriven.contracts.Events.PaymentDeclined;
import com.github.diovanibmotta.eventdriven.contracts.Events.StockRejected;
import com.github.diovanibmotta.eventdriven.contracts.MessagingConfig;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Initiating service: accepts the order, publishes {@code OrderPlaced} and then only <em>reacts</em> to the outcome
 * events. It never calls inventory or payment. State is in memory (didactic shortcut, see docs/04).
 */
@Service
public class OrderService {

    public enum Status { PENDING, CONFIRMED, CANCELLED }

    public record Order(String id, String customerId, String sku, int quantity, long amountCents,
                        Status status, String reason) {
        Order with(Status newStatus, String newReason) {
            return new Order(id, customerId, sku, quantity, amountCents, newStatus, newReason);
        }
    }

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final EventPublisher events;

    OrderService(EventPublisher events) {
        this.events = events;
    }

    public Order place(String customerId, String sku, int quantity, long unitPriceCents) {
        Order order = new Order(UUID.randomUUID().toString(), customerId, sku, quantity,
                unitPriceCents * quantity, Status.PENDING, null);
        orders.put(order.id(), order);
        events.publish(new OrderPlaced(order.id(), customerId, sku, quantity, order.amountCents()));
        return order;
    }

    public Optional<Order> find(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    @JmsListener(destination = Events.PAYMENT_APPROVED,
            subscription = "order-payment-approved", containerFactory = MessagingConfig.TOPIC_FACTORY)
    void on(PaymentApproved event) {
        settle(event.orderId(), Status.CONFIRMED, null);
    }

    @JmsListener(destination = Events.PAYMENT_DECLINED,
            subscription = "order-payment-declined", containerFactory = MessagingConfig.TOPIC_FACTORY)
    void on(PaymentDeclined event) {
        settle(event.orderId(), Status.CANCELLED, "payment declined: " + event.reason());
    }

    @JmsListener(destination = Events.STOCK_REJECTED,
            subscription = "order-stock-rejected", containerFactory = MessagingConfig.TOPIC_FACTORY)
    void on(StockRejected event) {
        settle(event.orderId(), Status.CANCELLED, "stock rejected: " + event.reason());
    }

    /** Idempotent: JMS is at-least-once, so a redelivered event must not change an already settled order. */
    private void settle(String orderId, Status status, String reason) {
        orders.computeIfPresent(orderId, (id, o) -> o.status() == Status.PENDING ? o.with(status, reason) : o);
    }
}
