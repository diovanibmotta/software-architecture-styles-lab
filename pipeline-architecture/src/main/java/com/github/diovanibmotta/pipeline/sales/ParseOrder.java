package com.github.diovanibmotta.pipeline.sales;

import com.github.diovanibmotta.pipeline.engine.Transformer;

import java.math.BigDecimal;

/** Transformer: CSV line {@code id,customer,country,quantity,unit_price,currency} to {@link Order}. Malformed lines throw. */
public class ParseOrder implements Transformer<String, Order> {

    @Override
    public Order transform(String line) {
        String[] f = line.split(",", -1);
        if (f.length != 6) {
            throw new IllegalArgumentException("expected 6 fields, got " + f.length);
        }
        return new Order(f[0].trim(), f[1].trim(), f[2].trim().toUpperCase(),
                Integer.parseInt(f[3].trim()), new BigDecimal(f[4].trim()), f[5].trim().toUpperCase());
    }
}
