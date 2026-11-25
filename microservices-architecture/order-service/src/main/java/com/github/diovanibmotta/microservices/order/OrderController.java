package com.github.diovanibmotta.microservices.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class OrderController {

    record PlaceOrder(@NotBlank String customerId, @Positive long itemId, @Min(1) @Max(10) int quantity,
                      @NotBlank String address) {
    }

    record DeliveryOutcome(@NotBlank String status, String courier) {
    }

    private final OrderService service;

    OrderController(OrderService service) {
        this.service = service;
    }

    /** 201 with the order: {@code PAID} (delivery requested, async) or {@code REJECTED} (payment declined). */
    @PostMapping("/orders")
    ResponseEntity<Order> place(@Valid @RequestBody PlaceOrder body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.place(body.customerId(), body.itemId(), body.quantity(), body.address()));
    }

    @GetMapping("/orders/{id}")
    ResponseEntity<Order> get(@PathVariable long id) {
        return service.find(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Internal callback used by the delivery-service (not routed by the gateway). */
    @PostMapping("/orders/{id}/delivery")
    ResponseEntity<Void> delivery(@PathVariable long id, @Valid @RequestBody DeliveryOutcome body) {
        service.onDeliveryOutcome(id, body.status(), body.courier());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(OrderService.ItemNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(OrderService.ItemNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    ResponseEntity<Map<String, String>> unavailable(DependencyUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
