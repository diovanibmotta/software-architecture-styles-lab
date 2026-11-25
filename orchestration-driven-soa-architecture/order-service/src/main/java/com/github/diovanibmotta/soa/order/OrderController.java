package com.github.diovanibmotta.soa.order;

import com.github.diovanibmotta.soa.contracts.Canonical.NewOrder;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderRecord;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderUpdate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController {

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/orders")
    ResponseEntity<OrderRecord> create(@RequestBody NewOrder order) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orders.create(order));
    }

    @GetMapping("/orders/{id}")
    ResponseEntity<OrderRecord> get(@PathVariable String id) {
        return orders.find(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/orders/{id}/status")
    ResponseEntity<OrderRecord> settle(@PathVariable String id, @RequestBody OrderUpdate update) {
        return orders.settle(id, update.status(), update.reason()).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
