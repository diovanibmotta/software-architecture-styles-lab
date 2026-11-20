package com.github.diovanibmotta.microkernel.plugins.pickup;

import com.github.diovanibmotta.microkernel.api.Quote;
import com.github.diovanibmotta.microkernel.api.QuoteRequest;
import com.github.diovanibmotta.microkernel.api.ShippingPlugin;
import java.math.BigDecimal;
import java.util.List;

/** Applies only when origin and destination share the first 2 ZIP digits and the parcel is up to 5 kg. */
public class PickupPlugin implements ShippingPlugin {

    static final double MAX_WEIGHT_KG = 5;

    @Override public String id() { return "pickup"; }

    @Override public String version() { return "1.0.0"; }

    @Override
    public List<Quote> quote(QuoteRequest r) {
        if (r.weightKg() > MAX_WEIGHT_KG || !sameRegion(r.originZip(), r.destinationZip())) {
            return List.of();
        }
        return List.of(new Quote("Pickup Point", "Collect at store", new BigDecimal("5.00"), 3));
    }

    private static boolean sameRegion(String a, String b) {
        return a != null && b != null && a.length() >= 2 && b.length() >= 2
                && a.substring(0, 2).equals(b.substring(0, 2));
    }
}
