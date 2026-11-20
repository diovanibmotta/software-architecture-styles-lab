package com.github.diovanibmotta.microkernel.plugins.express;

import com.github.diovanibmotta.microkernel.api.Quote;
import com.github.diovanibmotta.microkernel.api.QuoteRequest;
import com.github.diovanibmotta.microkernel.api.ShippingPlugin;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class ExpressPlugin implements ShippingPlugin {

    @Override public String id() { return "express"; }

    @Override public String version() { return "1.0.0"; }

    @Override
    public List<Quote> quote(QuoteRequest r) {
        BigDecimal price = BigDecimal.valueOf(25 + r.weightKg() * 4).setScale(2, RoundingMode.HALF_UP);
        return List.of(new Quote("Express Co.", "Next day", price, 1));
    }
}
