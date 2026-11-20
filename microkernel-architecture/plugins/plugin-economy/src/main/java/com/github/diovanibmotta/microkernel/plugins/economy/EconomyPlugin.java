package com.github.diovanibmotta.microkernel.plugins.economy;

import com.github.diovanibmotta.microkernel.api.Quote;
import com.github.diovanibmotta.microkernel.api.QuoteRequest;
import com.github.diovanibmotta.microkernel.api.ShippingPlugin;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class EconomyPlugin implements ShippingPlugin {

    @Override public String id() { return "economy"; }

    @Override public String version() { return "1.0.0"; }

    @Override
    public List<Quote> quote(QuoteRequest r) {
        BigDecimal price = BigDecimal.valueOf(8 + r.weightKg() * 1.5).setScale(2, RoundingMode.HALF_UP);
        return List.of(new Quote("Economy Post", "Standard", price, 7));
    }
}
