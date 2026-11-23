package com.github.diovanibmotta.pipeline.sales;

import com.github.diovanibmotta.pipeline.engine.Tester;

import java.util.Optional;
import java.util.Set;

/** Tester: business rules. Passes the order through untouched or rejects it with a reason. */
public class ValidateOrder implements Tester<Order> {

    private final Set<String> supportedCurrencies;

    public ValidateOrder(Set<String> supportedCurrencies) {
        this.supportedCurrencies = supportedCurrencies;
    }

    @Override
    public Optional<String> check(Order o) {
        if (o.quantity() <= 0) {
            return Optional.of("quantity must be positive");
        }
        if (o.unitPrice().signum() <= 0) {
            return Optional.of("unit price must be positive");
        }
        if (!supportedCurrencies.contains(o.currency())) {
            return Optional.of("unsupported currency " + o.currency());
        }
        return Optional.empty();
    }
}
