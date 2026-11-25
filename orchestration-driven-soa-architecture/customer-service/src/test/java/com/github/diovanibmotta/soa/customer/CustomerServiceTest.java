package com.github.diovanibmotta.soa.customer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerServiceTest {

    private final CustomerService service = new CustomerService("ana=5000");

    @Test
    void approvesWithinCreditLimit() {
        assertThat(service.checkCredit("ana", 5000).approved()).isTrue();
    }

    @Test
    void deniesAboveCreditLimit() {
        var result = service.checkCredit("ana", 5001);
        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo("credit limit exceeded");
    }

    @Test
    void deniesUnknownAndInactiveCustomers() {
        assertThat(service.checkCredit("ghost", 1).reason()).isEqualTo("unknown customer");
        service.define("zeca", false, 9999);
        assertThat(service.checkCredit("zeca", 1).reason()).isEqualTo("customer inactive");
    }
}
