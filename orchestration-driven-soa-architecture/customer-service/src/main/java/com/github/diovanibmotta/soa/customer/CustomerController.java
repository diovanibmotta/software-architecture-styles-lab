package com.github.diovanibmotta.soa.customer;

import com.github.diovanibmotta.soa.contracts.Canonical.CreditCheckRequest;
import com.github.diovanibmotta.soa.contracts.Canonical.CreditCheckResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CustomerController {

    record CustomerBody(boolean active, @Min(0) long creditLimitCents) {}

    private final CustomerService customers;

    CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @PostMapping("/credit-checks")
    CreditCheckResult check(@RequestBody CreditCheckRequest r) {
        return customers.checkCredit(r.customerId(), r.amountCents());
    }

    @PutMapping("/customers/{id}")
    CustomerService.Customer define(@PathVariable String id, @Valid @RequestBody CustomerBody body) {
        return customers.define(id, body.active(), body.creditLimitCents());
    }
}
