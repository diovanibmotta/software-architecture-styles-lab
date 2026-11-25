package com.github.diovanibmotta.soa.billing;

import com.github.diovanibmotta.soa.contracts.Canonical.ChargeRequest;
import com.github.diovanibmotta.soa.contracts.Canonical.ChargeResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Reusable business service: charges customers. Decision is stored per order id, so retries are idempotent. */
@Service
public class BillingService {

    private final Map<String, ChargeResult> charges = new ConcurrentHashMap<>();
    private final long limitCents;

    BillingService(@Value("${billing.limit-cents:1000000}") long limitCents) {
        this.limitCents = limitCents;
    }

    public ChargeResult charge(ChargeRequest r) {
        return charges.computeIfAbsent(r.orderId(), id -> r.amountCents() > limitCents
                ? new ChargeResult(id, false, "amount above the " + limitCents + " cents limit")
                : new ChargeResult(id, true, null));
    }

    public Optional<ChargeResult> find(String orderId) {
        return Optional.ofNullable(charges.get(orderId));
    }
}
