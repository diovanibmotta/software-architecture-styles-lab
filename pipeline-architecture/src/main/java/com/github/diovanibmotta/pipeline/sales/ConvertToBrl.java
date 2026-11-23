package com.github.diovanibmotta.pipeline.sales;

import com.github.diovanibmotta.pipeline.engine.Transformer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Transformer: order total in its own currency to BRL, using a fixed rate table. */
public class ConvertToBrl implements Transformer<Order, BrlOrder> {

    private final Map<String, BigDecimal> ratesToBrl;

    public ConvertToBrl(Map<String, BigDecimal> ratesToBrl) {
        this.ratesToBrl = ratesToBrl;
    }

    @Override
    public BrlOrder transform(Order o) {
        BigDecimal total = o.unitPrice()
                .multiply(BigDecimal.valueOf(o.quantity()))
                .multiply(ratesToBrl.get(o.currency()))
                .setScale(2, RoundingMode.HALF_EVEN);
        return new BrlOrder(o.id(), o.customer(), o.country(), total);
    }
}
