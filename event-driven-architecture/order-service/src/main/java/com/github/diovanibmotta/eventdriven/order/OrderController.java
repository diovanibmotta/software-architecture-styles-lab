package com.github.diovanibmotta.eventdriven.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController {

    record PlaceOrderRequest(@NotBlank String customerId, @NotBlank String sku,
                             @Min(1) @Max(10) int quantity, @Min(1) long unitPriceCents) {}

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    /** 202, not 201: the order is accepted, its outcome is decided later by other services. Poll GET. */
    @PostMapping("/orders")
    ResponseEntity<OrderService.Order> place(@Valid @RequestBody PlaceOrderRequest r) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(orders.place(r.customerId(), r.sku(), r.quantity(), r.unitPriceCents()));
    }

    @GetMapping("/orders/{id}")
    ResponseEntity<OrderService.Order> get(@PathVariable String id) {
        return orders.find(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
