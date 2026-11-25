package com.github.diovanibmotta.eventdriven.notification;

import com.github.diovanibmotta.eventdriven.contracts.Events;
import com.github.diovanibmotta.eventdriven.contracts.Events.PaymentApproved;
import com.github.diovanibmotta.eventdriven.contracts.Events.PaymentDeclined;
import com.github.diovanibmotta.eventdriven.contracts.Events.StockRejected;
import com.github.diovanibmotta.eventdriven.contracts.MessagingConfig;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure subscriber: nobody calls it and no other service knows it exists. It was added after the order flow was
 * working, without touching order/inventory/payment (the extensibility the style promises).
 * Keyed by orderId + kind so a redelivered event does not send the customer a duplicate message.
 */
@Service
public class NotificationService {

    public record Notification(String orderId, String customerId, String message) {}

    private final Map<String, Notification> sent = new ConcurrentHashMap<>();

    public List<Notification> forCustomer(String customerId) {
        return sent.values().stream().filter(n -> n.customerId().equals(customerId)).toList();
    }

    @JmsListener(destination = Events.PAYMENT_APPROVED, subscription = "notification-payment-approved",
            containerFactory = MessagingConfig.TOPIC_FACTORY)
    void on(PaymentApproved e) {
        notify(e.orderId(), "approved", e.customerId(), "Order " + e.orderId() + " confirmed.");
    }

    @JmsListener(destination = Events.PAYMENT_DECLINED, subscription = "notification-payment-declined",
            containerFactory = MessagingConfig.TOPIC_FACTORY)
    void on(PaymentDeclined e) {
        notify(e.orderId(), "declined", e.customerId(), "Order " + e.orderId() + " cancelled: " + e.reason());
    }

    @JmsListener(destination = Events.STOCK_REJECTED, subscription = "notification-stock-rejected",
            containerFactory = MessagingConfig.TOPIC_FACTORY)
    void on(StockRejected e) {
        notify(e.orderId(), "rejected", e.customerId(), "Order " + e.orderId() + " cancelled: " + e.reason());
    }

    private void notify(String orderId, String kind, String customerId, String message) {
        sent.putIfAbsent(orderId + ":" + kind, new Notification(orderId, customerId, message));
    }
}
