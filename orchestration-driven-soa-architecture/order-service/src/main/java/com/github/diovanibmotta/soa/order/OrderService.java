package com.github.diovanibmotta.soa.order;

import com.github.diovanibmotta.soa.contracts.Canonical.NewOrder;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderRecord;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Reusable business service: system of record for orders. It only stores; the workflow lives in the orchestrator. */
@Service
public class OrderService {

    private final Map<String, OrderRecord> orders = new ConcurrentHashMap<>();

    public OrderRecord create(NewOrder n) {
        OrderRecord order = new OrderRecord(UUID.randomUUID().toString(), n.customerId(), n.sku(), n.quantity(),
                n.amountCents(), OrderStatus.CREATED, null);
        orders.put(order.id(), order);
        return order;
    }

    public Optional<OrderRecord> find(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    /** Only a CREATED order can be settled; a repeated call (retry) leaves the first outcome untouched. */
    public Optional<OrderRecord> settle(String id, OrderStatus status, String reason) {
        return Optional.ofNullable(orders.computeIfPresent(id, (k, o) -> o.status() == OrderStatus.CREATED
                ? new OrderRecord(o.id(), o.customerId(), o.sku(), o.quantity(), o.amountCents(), status, reason) : o));
    }
}
