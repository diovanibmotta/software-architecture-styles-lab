package com.github.diovanibmotta.soa.billing;

import com.github.diovanibmotta.soa.contracts.Canonical.ChargeRequest;
import com.github.diovanibmotta.soa.contracts.Canonical.ChargeResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class BillingController {

    private final BillingService billing;

    BillingController(BillingService billing) {
        this.billing = billing;
    }

    @PostMapping("/charges")
    ChargeResult charge(@RequestBody ChargeRequest request) {
        return billing.charge(request);
    }

    @GetMapping("/charges/{orderId}")
    ResponseEntity<ChargeResult> get(@PathVariable String orderId) {
        return billing.find(orderId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
