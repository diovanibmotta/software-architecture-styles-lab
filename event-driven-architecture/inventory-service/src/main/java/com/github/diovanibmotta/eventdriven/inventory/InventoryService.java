package com.github.diovanibmotta.eventdriven.inventory;

import com.github.diovanibmotta.eventdriven.contracts.EventPublisher;
import com.github.diovanibmotta.eventdriven.contracts.Events;
import com.github.diovanibmotta.eventdriven.contracts.Events.OrderPlaced;
import com.github.diovanibmotta.eventdriven.contracts.Events.PaymentDeclined;
import com.github.diovanibmotta.eventdriven.contracts.Events.StockRejected;
import com.github.diovanibmotta.eventdriven.contracts.Events.StockReserved;
import com.github.diovanibmotta.eventdriven.contracts.MessagingConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Owns the stock. Reserves on {@code OrderPlaced}; gives the stock back on {@code PaymentDeclined} (a compensating
 * action: there is no distributed transaction, the services undo each other's effects through events).
 * <p>
 * Handlers are idempotent: a redelivered event replays the original outcome instead of reserving twice.
 */
@Service
public class InventoryService {

    private record Reservation(String sku, int quantity) {}

    private final Map<String, Integer> stock = new HashMap<>();
    private final Map<String, Reservation> reservations = new HashMap<>();   // orderId -> held stock
    private final Map<String, String> rejections = new HashMap<>();          // orderId -> reason
    private final EventPublisher events;

    /** @param initial optional seed, e.g. {@code console=5,phone=100} (env var {@code INVENTORY_INITIAL}) */
    InventoryService(EventPublisher events, @Value("${inventory.initial:}") String initial) {
        this.events = events;
        if (!initial.isBlank()) {
            for (String pair : initial.split(",")) {
                String[] kv = pair.split("=");
                stock.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
            }
        }
    }

    public synchronized void setStock(String sku, int quantity) {
        stock.put(sku, quantity);
    }

    public synchronized int stockOf(String sku) {
        return stock.getOrDefault(sku, 0);
    }

    @JmsListener(destination = Events.ORDER_PLACED, subscription = "inventory-order-placed",
            containerFactory = MessagingConfig.TOPIC_FACTORY)
    void on(OrderPlaced event) {
        events.publish(reserve(event));
    }

    @JmsListener(destination = Events.PAYMENT_DECLINED, subscription = "inventory-payment-declined",
            containerFactory = MessagingConfig.TOPIC_FACTORY)
    void on(PaymentDeclined event) {
        release(event.orderId());
    }

    private synchronized Object reserve(OrderPlaced e) {
        if (reservations.containsKey(e.orderId()) || rejections.containsKey(e.orderId())) {
            // redelivery: nothing changes, the original outcome is published again (consumers are idempotent too)
            String reason = rejections.get(e.orderId());
            return reason == null ? new StockReserved(e.orderId(), e.customerId(), e.amountCents())
                    : new StockRejected(e.orderId(), e.customerId(), reason);
        }
        int available = stock.getOrDefault(e.sku(), 0);
        if (available < e.quantity()) {
            String reason = "only " + available + " of " + e.sku() + " left";
            rejections.put(e.orderId(), reason);
            return new StockRejected(e.orderId(), e.customerId(), reason);
        }
        stock.put(e.sku(), available - e.quantity());
        reservations.put(e.orderId(), new Reservation(e.sku(), e.quantity()));
        return new StockReserved(e.orderId(), e.customerId(), e.amountCents());
    }

    private synchronized void release(String orderId) {
        Reservation held = reservations.remove(orderId);   // null on redelivery -> no double release
        if (held != null) {
            stock.merge(held.sku(), held.quantity(), Integer::sum);
        }
    }
}
