package com.github.diovanibmotta.microkernel.plugins.express;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.diovanibmotta.microkernel.api.QuoteRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ExpressPluginTest {

    @Test
    void quotesNextDayWithWeightBasedPrice() {
        var quotes = new ExpressPlugin().quote(new QuoteRequest("01000", "20000", 2));
        assertThat(quotes).singleElement().satisfies(q -> {
            assertThat(q.price()).isEqualByComparingTo(new BigDecimal("33.00"));
            assertThat(q.deliveryDays()).isEqualTo(1);
        });
    }
}
