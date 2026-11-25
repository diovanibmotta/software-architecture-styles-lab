package com.github.diovanibmotta.eventdriven.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class InventoryController {

    record StockRequest(@Min(0) int quantity) {}

    private final InventoryService inventory;

    InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @PutMapping("/stock/{sku}")
    Map<String, Object> set(@PathVariable String sku, @Valid @RequestBody StockRequest r) {
        inventory.setStock(sku, r.quantity());
        return Map.of("sku", sku, "quantity", r.quantity());
    }

    @GetMapping("/stock/{sku}")
    Map<String, Object> get(@PathVariable String sku) {
        return Map.of("sku", sku, "quantity", inventory.stockOf(sku));
    }
}
