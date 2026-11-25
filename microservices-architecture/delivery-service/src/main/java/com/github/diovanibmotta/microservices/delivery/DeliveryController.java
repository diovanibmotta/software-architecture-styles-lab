package com.github.diovanibmotta.microservices.delivery;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@RestController
class DeliveryController {

    record DispatchRequest(@Positive long orderId, @NotBlank String address) {
    }

    private final DeliveryRepository deliveries;
    private final OrderCallback orders;
    private final int couriers;

    DeliveryController(DeliveryRepository deliveries, OrderCallback orders, @Value("${delivery.couriers}") int couriers) {
        this.deliveries = deliveries;
        this.orders = orders;
        this.couriers = couriers;
    }

    /**
     * Called by the order-service's outbox relay (at-least-once), so it must be idempotent: a repeated request for the
     * same order reuses the stored decision and just re-sends the callback. 5xx tells the relay to retry later.
     */
    @PostMapping("/deliveries")
    ResponseEntity<Delivery> dispatch(@Valid @RequestBody DispatchRequest request) {
        Delivery delivery = deliveries.findByOrderId(request.orderId()).orElseGet(() -> deliveries.save(assign(request)));
        return report(delivery, HttpStatus.ACCEPTED);
    }

    @GetMapping("/deliveries/{orderId}")
    ResponseEntity<Delivery> get(@PathVariable long orderId) {
        return deliveries.findByOrderId(orderId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** The courier dropped the food: frees the courier and tells the order. */
    @PostMapping("/deliveries/{orderId}/complete")
    ResponseEntity<Delivery> complete(@PathVariable long orderId) {
        Optional<Delivery> found = deliveries.findByOrderId(orderId);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Delivery delivery = found.get();
        if (delivery.status() == Delivery.Status.NO_COURIER) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(delivery);
        }
        if (delivery.status() == Delivery.Status.ASSIGNED) {
            delivery = deliveries.save(delivery.withStatus(Delivery.Status.DELIVERED));
        }
        return report(delivery, HttpStatus.OK);
    }

    private Delivery assign(DispatchRequest request) {
        long busy = deliveries.countByStatus(Delivery.Status.ASSIGNED);
        if (busy >= couriers) {
            return new Delivery(null, request.orderId(), request.address(), Delivery.Status.NO_COURIER, null);
        }
        return new Delivery(null, request.orderId(), request.address(), Delivery.Status.ASSIGNED, "courier-" + (busy + 1));
    }

    private ResponseEntity<Delivery> report(Delivery delivery, HttpStatus ok) {
        try {
            orders.report(delivery);
            return ResponseEntity.status(ok).body(delivery);
        } catch (RestClientException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(delivery);
        }
    }
}
