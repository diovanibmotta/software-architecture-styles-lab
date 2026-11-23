package com.github.diovanibmotta.pipeline.sales;

import com.github.diovanibmotta.pipeline.engine.PipelineReport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** CLI: {@code java -cp target/classes com.github.diovanibmotta.pipeline.sales.PipelineApplication [orders.csv]} (defaults to the bundled sample). */
public final class PipelineApplication {

    private PipelineApplication() {
    }

    public static void main(String[] args) throws IOException {
        try (BufferedReader reader = args.length > 0
                ? Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)
                : new BufferedReader(new InputStreamReader(
                        PipelineApplication.class.getResourceAsStream("/orders.csv"), StandardCharsets.UTF_8))) {
            var summary = new SalesSummary();
            PipelineReport report = SalesPipelines.salesReport(new CsvLinesProducer(reader), summary).run();

            System.out.printf("produced=%d consumed=%d rejected=%d%n",
                    report.produced(), report.consumed(), report.deadLetters().size());
            summary.revenueByCountry().forEach((country, total) -> System.out.printf("  %s  R$ %s%n", country, total));
            System.out.printf("  TOTAL R$ %s%n", summary.total());
            report.deadLetters().forEach(d -> System.out.printf("  dead-letter [%s] %s -> %s%n", d.stage(), d.item(), d.reason()));
        }
    }
}
