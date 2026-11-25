package com.github.diovanibmotta.microservices.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

/**
 * The saga. Steps 1-2 are synchronous (the customer waits for the charge); step 3 (delivery) is asynchronous through
 * the outbox because it may take a while and the delivery-service may be down.
 * <pre>
 *  1. catalog.find       -> price               (fail: 404 / 503, no order stored)
 *  2. payment.authorize  -> PAID | REJECTED     (compensation of a later failure: REFUND)
 *  3. outbox DISPATCH    -> delivery-service -> callback: CONFIRMED | CANCELLED (+ outbox REFUND)
 * </pre>
 */
@Service
public class OrderService {

    public static class ItemNotFoundException extends RuntimeException {
        ItemNotFoundException(long itemId) {
            super("item " + itemId + " not found");
        }
    }

    private final OrderRepository orders;
    private final OutboxRepository outbox;
    private final CatalogClient catalog;
    private final PaymentClient payments;
    private final TransactionTemplate tx;
    private final OutboxPayloads payloads;

    OrderService(OrderRepository orders, OutboxRepository outbox, CatalogClient catalog, PaymentClient payments,
                 TransactionTemplate tx, OutboxPayloads payloads) {
        this.orders = orders;
        this.outbox = outbox;
        this.catalog = catalog;
        this.payments = payments;
        this.tx = tx;
        this.payloads = payloads;
    }

    public Order place(String customerId, long itemId, int quantity, String address) {
        CatalogClient.Item item = catalog.find(itemId).orElseThrow(() -> new ItemNotFoundException(itemId));
        Order order = orders.save(new Order(null, customerId, itemId, quantity, address, item.priceCents() * quantity,
                Order.Status.PENDING, null, null, null));

        PaymentClient.Payment payment;
        try {
            payment = payments.authorize(order.id(), order.totalCents());
        } catch (DependencyUnavailableException e) {
            orders.save(order.with(Order.Status.REJECTED, "payment unavailable"));   // nothing was charged (or it will be refunded on retry-less rejection)
            throw e;
        }
        if (!payment.authorized()) {
            return orders.save(order.with(Order.Status.REJECTED, "payment declined"));
        }
        // PAID and the DISPATCH message commit together: no paid order without a delivery request, and vice versa
        return tx.execute(status -> {
            Order paid = orders.save(order.paid(payment.id()));
            outbox.save(new OutboxMessage(null, OutboxMessage.DISPATCH, paid.id(), payloads.dispatch(paid), false, 0));
            return paid;
        });
    }

    public Optional<Order> find(long id) {
        return orders.findById(id);
    }

    /** Callback from the delivery-service. Idempotent: it can arrive more than once, or after a cancellation. */
    public void onDeliveryOutcome(long orderId, String deliveryStatus, String courier) {
        tx.executeWithoutResult(status -> orders.findById(orderId).ifPresent(order -> {
            switch (deliveryStatus) {
                case "ASSIGNED" -> {
                    if (order.status() == Order.Status.PAID) orders.save(order.confirmed(courier));
                }
                case "DELIVERED" -> {
                    if (order.status() == Order.Status.CONFIRMED) orders.save(order.with(Order.Status.DELIVERED, null));
                }
                case "NO_COURIER" -> {
                    if (order.status() == Order.Status.PAID) {   // compensation: undo the charge
                        orders.save(order.with(Order.Status.CANCELLED, "no courier available"));
                        outbox.save(new OutboxMessage(null, OutboxMessage.REFUND, orderId, payloads.refund(order), false, 0));
                    }
                }
                default -> throw new IllegalArgumentException("unknown delivery status " + deliveryStatus);
            }
        }));
    }
}
