package com.github.diovanibmotta.soa.billing;

import com.github.diovanibmotta.soa.contracts.Canonical.ChargeRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillingServiceTest {

    private final BillingService billing = new BillingService(1000);

    @Test
    void approvesUpToTheLimit() {
        assertThat(billing.charge(new ChargeRequest("o1", "ana", 1000)).approved()).isTrue();
    }

    @Test
    void declinesAboveTheLimit() {
        var result = billing.charge(new ChargeRequest("o2", "ana", 1001));
        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).contains("limit");
    }

    @Test
    void decisionIsStoredPerOrderSoRetriesAreIdempotent() {
        var first = billing.charge(new ChargeRequest("o3", "ana", 10));
        assertThat(billing.charge(new ChargeRequest("o3", "ana", 99_999))).isEqualTo(first);
        assertThat(billing.find("o3")).contains(first);
    }
}
