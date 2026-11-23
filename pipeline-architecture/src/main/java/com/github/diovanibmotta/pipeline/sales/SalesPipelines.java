package com.github.diovanibmotta.pipeline.sales;

import com.github.diovanibmotta.pipeline.engine.Pipeline;
import com.github.diovanibmotta.pipeline.engine.Producer;

import java.math.BigDecimal;
import java.util.Map;

/** Wires the sales ETL: producer, parse, validate, convert, consumer. */
public final class SalesPipelines {

    public static final Map<String, BigDecimal> RATES_TO_BRL = Map.of(
            "BRL", new BigDecimal("1.00"),
            "USD", new BigDecimal("5.00"),
            "EUR", new BigDecimal("5.50"));

    private SalesPipelines() {
    }

    public static Pipeline salesReport(Producer<String> source, SalesSummary summary) {
        return Pipeline.from(source)
                .transform("parse", new ParseOrder())
                .test("validate", new ValidateOrder(RATES_TO_BRL.keySet()))
                .transform("convert-to-brl", new ConvertToBrl(RATES_TO_BRL))
                .to("summarize", summary);
    }
}
