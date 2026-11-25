package com.github.diovanibmotta.soa.order;

import com.github.diovanibmotta.soa.contracts.Canonical.NewOrder;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceTest {

    private final OrderService orders = new OrderService();

    @Test
    void createsOrderInCreatedStatus() {
        var order = orders.create(new NewOrder("ana", "console", 1, 300));
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(orders.find(order.id())).contains(order);
    }

    @Test
    void firstSettlementWinsSoRetriesAreIdempotent() {
        var order = orders.create(new NewOrder("ana", "console", 1, 300));
        orders.settle(order.id(), OrderStatus.CONFIRMED, null);
        var again = orders.settle(order.id(), OrderStatus.CANCELLED, "late");
        assertThat(again.orElseThrow().status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void settlingUnknownOrderReturnsEmpty() {
        assertThat(orders.settle("nope", OrderStatus.CANCELLED, "x")).isEmpty();
    }
}
