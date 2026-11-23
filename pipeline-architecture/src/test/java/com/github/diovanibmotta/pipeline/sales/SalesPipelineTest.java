package com.github.diovanibmotta.pipeline.sales;

import com.github.diovanibmotta.pipeline.engine.PipelineReport;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SalesPipelineTest {

    @Test
    void sampleFileProducesSummaryAndRejectsBadRows() throws IOException {
        var summary = new SalesSummary();
        PipelineReport report;
        try (var reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/orders.csv"), StandardCharsets.UTF_8))) {
            report = SalesPipelines.salesReport(new CsvLinesProducer(reader), summary).run();
        }

        assertThat(report.produced()).isEqualTo(9);
        assertThat(report.consumed()).isEqualTo(4);
        assertThat(report.deadLetters()).extracting(d -> d.stage() + ": " + d.item().toString().split(",")[0])
                .containsExactlyInAnyOrder("validate: Order[id=1004", "validate: Order[id=1005", "validate: Order[id=1006",
                        "parse: 1007", "parse: this");
        // BR 200.00 | US 40*5 + 2*25.50*5 = 455.00 | DE 3*10*5.50 = 165.00
        assertThat(summary.revenueByCountry()).containsExactly(
                java.util.Map.entry("BR", new BigDecimal("200.00")),
                java.util.Map.entry("DE", new BigDecimal("165.00")),
                java.util.Map.entry("US", new BigDecimal("455.00")));
        assertThat(summary.total()).isEqualByComparingTo("820.00");
    }

    @Test
    void largeInputKeepsEveryValidOrder() throws IOException {
        var csv = new StringBuilder("id,customer,country,quantity,unit_price,currency\n");
        for (int i = 0; i < 5000; i++) {
            csv.append(i).append(",c,BR,1,2.00,BRL\n");
        }
        var summary = new SalesSummary();
        var report = SalesPipelines.salesReport(
                new CsvLinesProducer(new BufferedReader(new StringReader(csv.toString()))), summary).run();

        assertThat(report.consumed()).isEqualTo(5000);
        assertThat(summary.total()).isEqualByComparingTo("10000.00");
    }
}
