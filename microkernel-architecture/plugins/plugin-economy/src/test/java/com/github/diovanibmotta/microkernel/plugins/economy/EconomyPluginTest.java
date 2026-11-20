package com.github.diovanibmotta.microkernel.plugins.economy;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.diovanibmotta.microkernel.api.QuoteRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EconomyPluginTest {

    @Test
    void quotesCheapAndSlow() {
        var quotes = new EconomyPlugin().quote(new QuoteRequest("01000", "20000", 2));
        assertThat(quotes).singleElement().satisfies(q -> {
            assertThat(q.price()).isEqualByComparingTo(new BigDecimal("11.00"));
            assertThat(q.deliveryDays()).isEqualTo(7);
        });
    }
}
