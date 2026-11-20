package com.github.diovanibmotta.microkernel.plugins.pickup;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.diovanibmotta.microkernel.api.QuoteRequest;
import org.junit.jupiter.api.Test;

class PickupPluginTest {

    private final PickupPlugin plugin = new PickupPlugin();

    @Test
    void appliesToSameRegionLightParcel() {
        assertThat(plugin.quote(new QuoteRequest("01000", "01999", 2))).hasSize(1);
    }

    @Test
    void notApplicableToOtherRegionOrHeavyParcel() {
        assertThat(plugin.quote(new QuoteRequest("01000", "20000", 2))).isEmpty();
        assertThat(plugin.quote(new QuoteRequest("01000", "01999", 6))).isEmpty();
    }
}
