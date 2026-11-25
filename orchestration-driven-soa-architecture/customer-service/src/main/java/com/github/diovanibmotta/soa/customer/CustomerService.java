package com.github.diovanibmotta.soa.customer;

import com.github.diovanibmotta.soa.contracts.Canonical.CreditCheckResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reusable business service: owns who a customer is and how much credit they have. Knows nothing about orders. */
@Service
public class CustomerService {

    public record Customer(String id, boolean active, long creditLimitCents) {}

    private final Map<String, Customer> customers = new ConcurrentHashMap<>();

    CustomerService(@Value("${customer.initial:}") String initial) {
        for (String entry : initial.split(",")) {
            if (!entry.isBlank()) {
                String[] kv = entry.trim().split("=");
                define(kv[0], true, Long.parseLong(kv[1]));
            }
        }
    }

    public Customer define(String id, boolean active, long creditLimitCents) {
        Customer customer = new Customer(id, active, creditLimitCents);
        customers.put(id, customer);
        return customer;
    }

    public CreditCheckResult checkCredit(String customerId, long amountCents) {
        Customer customer = customers.get(customerId);
        if (customer == null) return new CreditCheckResult(false, "unknown customer");
        if (!customer.active()) return new CreditCheckResult(false, "customer inactive");
        if (amountCents > customer.creditLimitCents()) return new CreditCheckResult(false, "credit limit exceeded");
        return new CreditCheckResult(true, null);
    }
}
