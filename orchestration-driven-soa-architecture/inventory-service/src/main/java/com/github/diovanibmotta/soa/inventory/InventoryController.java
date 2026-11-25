package com.github.diovanibmotta.soa.inventory;

import com.github.diovanibmotta.soa.contracts.Canonical.ReserveRequest;
import com.github.diovanibmotta.soa.contracts.Canonical.Reservation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class InventoryController {

    record StockBody(@Min(0) int quantity) {}

    private final InventoryService inventory;

    InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/stock/{sku}")
    Map<String, Object> stock(@PathVariable String sku) {
        return Map.of("sku", sku, "quantity", inventory.stock(sku));
    }

    @PutMapping("/stock/{sku}")
    Map<String, Object> setStock(@PathVariable String sku, @Valid @RequestBody StockBody body) {
        inventory.setStock(sku, body.quantity());
        return Map.of("sku", sku, "quantity", body.quantity());
    }

    @PostMapping("/reservations")
    Reservation reserve(@RequestBody ReserveRequest request) {
        return inventory.reserve(request);
    }

    @DeleteMapping("/reservations/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(@PathVariable String orderId) {
        inventory.release(orderId);
    }
}
