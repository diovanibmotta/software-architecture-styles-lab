package com.github.diovanibmotta.pipeline.sales;

import com.github.diovanibmotta.pipeline.engine.Sink;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

/** Consumer: accumulates revenue per country. Only the last filter's single thread writes; read it after {@code run()} returns. */
public class SalesSummary implements Sink<BrlOrder> {

    private final Map<String, BigDecimal> revenueByCountry = new TreeMap<>();

    @Override
    public void consume(BrlOrder order) {
        revenueByCountry.merge(order.country(), order.totalBrl(), BigDecimal::add);
    }

    public Map<String, BigDecimal> revenueByCountry() {
        return revenueByCountry;
    }

    public BigDecimal total() {
        return revenueByCountry.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
