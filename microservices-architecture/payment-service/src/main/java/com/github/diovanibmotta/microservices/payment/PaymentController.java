package com.github.diovanibmotta.microservices.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
class PaymentController {

    record AuthorizeRequest(@Positive long orderId, @Positive long amountCents) {
    }

    private final PaymentRepository payments;
    private final long limitCents;

    PaymentController(PaymentRepository payments, @Value("${payment.limit-cents}") long limitCents) {
        this.payments = payments;
        this.limitCents = limitCents;
    }

    /** Idempotent per order: the caller may retry after a timeout without charging twice. */
    @PostMapping("/payments")
    ResponseEntity<Payment> authorize(@Valid @RequestBody AuthorizeRequest request) {
        Optional<Payment> existing = payments.findByOrderId(request.orderId());
        if (existing.isPresent()) {
            return ResponseEntity.ok(existing.get());
        }
        Payment.Status status = request.amountCents() > limitCents ? Payment.Status.DECLINED : Payment.Status.AUTHORIZED;
        Payment saved = payments.save(new Payment(null, request.orderId(), request.amountCents(), status));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/payments/{id}")
    ResponseEntity<Payment> get(@PathVariable long id) {
        return payments.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Compensation of {@code authorize}. Idempotent: refunding a refunded payment is a no-op. */
    @PostMapping("/payments/{id}/refund")
    ResponseEntity<Payment> refund(@PathVariable long id) {
        return payments.findById(id).map(p -> switch (p.status()) {
            case REFUNDED -> ResponseEntity.ok(p);
            case AUTHORIZED -> ResponseEntity.ok(payments.save(p.withStatus(Payment.Status.REFUNDED)));
            case DECLINED -> ResponseEntity.status(HttpStatus.CONFLICT).body(p);   // nothing was charged
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
