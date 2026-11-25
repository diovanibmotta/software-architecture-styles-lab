package com.github.diovanibmotta.eventdriven.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PaymentController {

    private final PaymentService payments;

    PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping("/payments/{orderId}")
    ResponseEntity<PaymentService.Payment> get(@PathVariable String orderId) {
        return payments.find(orderId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
