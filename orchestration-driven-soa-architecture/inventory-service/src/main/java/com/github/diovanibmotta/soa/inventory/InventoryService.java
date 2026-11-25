package com.github.diovanibmotta.soa.inventory;

import com.github.diovanibmotta.soa.contracts.Canonical.ReserveRequest;
import com.github.diovanibmotta.soa.contracts.Canonical.Reservation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/** Reusable business service: owns stock. Reservations are keyed by order id so that release is idempotent. */
@Service
public class InventoryService {

    private record Held(String sku, int quantity) {}

    private final Map<String, Integer> stock = new HashMap<>();
    private final Map<String, Held> reservations = new HashMap<>();

    InventoryService(@Value("${inventory.initial:}") String initial) {
        for (String entry : initial.split(",")) {
            if (!entry.isBlank()) {
                String[] kv = entry.trim().split("=");
                stock.put(kv[0], Integer.parseInt(kv[1]));
            }
        }
    }

    public synchronized int stock(String sku) {
        return stock.getOrDefault(sku, 0);
    }

    public synchronized void setStock(String sku, int quantity) {
        stock.put(sku, quantity);
    }

    /** Check-and-decrement is atomic (one lock): concurrent orders can never oversell. Idempotent per order id. */
    public synchronized Reservation reserve(ReserveRequest r) {
        if (reservations.containsKey(r.orderId())) return new Reservation(r.orderId(), true, null);
        int available = stock(r.sku());
        if (available < r.quantity()) {
            return new Reservation(r.orderId(), false, "insufficient stock for " + r.sku() + ": " + available + " available");
        }
        stock.put(r.sku(), available - r.quantity());
        reservations.put(r.orderId(), new Held(r.sku(), r.quantity()));
        return new Reservation(r.orderId(), true, null);
    }

    /** Compensation of {@link #reserve}: returns the units, and does nothing if there is nothing to return. */
    public synchronized void release(String orderId) {
        Held held = reservations.remove(orderId);
        if (held != null) stock.merge(held.sku(), held.quantity(), Integer::sum);
    }
}
