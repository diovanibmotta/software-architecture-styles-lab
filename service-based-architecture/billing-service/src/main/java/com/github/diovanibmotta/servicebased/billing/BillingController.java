package com.github.diovanibmotta.servicebased.billing;

import com.github.diovanibmotta.servicebased.shared.Invoice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
class BillingController {

    record Payment(@NotNull Long orderId) {
    }

    private final BillingService service;

    BillingController(BillingService service) {
        this.service = service;
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    Invoice pay(@Valid @RequestBody Payment request) {
        return service.pay(request.orderId());
    }

    @GetMapping("/invoices/{orderId}")
    Invoice invoice(@PathVariable Long orderId) {
        return service.invoiceOf(orderId);
    }
}
