package com.github.diovanibmotta.soa.inventory;

import com.github.diovanibmotta.soa.contracts.Canonical.ReserveRequest;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryServiceTest {

    private final InventoryService inventory = new InventoryService("console=2");

    @Test
    void reservesAndDecrementsStock() {
        assertThat(inventory.reserve(new ReserveRequest("o1", "console", 1)).reserved()).isTrue();
        assertThat(inventory.stock("console")).isEqualTo(1);
    }

    @Test
    void rejectsWhenInsufficientAndLeavesStockUntouched() {
        var result = inventory.reserve(new ReserveRequest("o1", "console", 3));
        assertThat(result.reserved()).isFalse();
        assertThat(result.reason()).contains("insufficient stock");
        assertThat(inventory.stock("console")).isEqualTo(2);
    }

    @Test
    void reserveIsIdempotentPerOrder() {
        inventory.reserve(new ReserveRequest("o1", "console", 1));
        inventory.reserve(new ReserveRequest("o1", "console", 1));
        assertThat(inventory.stock("console")).isEqualTo(1);
    }

    @Test
    void releaseReturnsUnitsOnceAndIgnoresUnknownOrders() {
        inventory.reserve(new ReserveRequest("o1", "console", 2));
        inventory.release("o1");
        inventory.release("o1");
        inventory.release("never-reserved");
        assertThat(inventory.stock("console")).isEqualTo(2);
    }

    @Test
    void neverOversellsUnderConcurrency() {
        inventory.setStock("ticket", 5);
        long reserved = IntStream.range(0, 50).parallel()
                .filter(i -> inventory.reserve(new ReserveRequest("o" + i, "ticket", 1)).reserved()).count();
        assertThat(reserved).isEqualTo(5);
        assertThat(inventory.stock("ticket")).isZero();
    }
}
