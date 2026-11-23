package com.github.diovanibmotta.pipeline.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineTest {

    @Test
    void flowsThroughFiltersInOrderAndCollectsDeadLetters() {
        List<Integer> out = Collections.synchronizedList(new ArrayList<>());

        PipelineReport report = Pipeline.<Integer>from(emit -> IntStream.rangeClosed(1, 1000).forEach(emit::accept))
                .pipeCapacity(2) // tiny pipes force back-pressure
                .test("even-only", n -> n % 2 == 0 ? Optional.empty() : Optional.of("odd"))
                .transform("explode-on-100", n -> {
                    if (n == 100) {
                        throw new IllegalStateException("boom");
                    }
                    return n * 10;
                })
                .to("collect", out::add)
                .run();

        assertThat(report.produced()).isEqualTo(1000);
        assertThat(report.consumed()).isEqualTo(499);
        assertThat(report.deadLetters()).hasSize(501);
        assertThat(report.deadLetters()).filteredOn(d -> d.stage().equals("explode-on-100"))
                .singleElement().satisfies(d -> {
                    assertThat(d.item()).isEqualTo(100);
                    assertThat(d.reason()).contains("boom");
                });
        assertThat(out).isSorted().first().isEqualTo(20);
    }

    @Test
    void producerFailureSurfacesAsPipelineException() {
        var pipeline = Pipeline.<Integer>from(emit -> {
            emit.accept(1);
            throw new java.io.IOException("disk gone");
        }).to("sink", n -> { });

        assertThatThrownBy(pipeline::run)
                .isInstanceOf(PipelineException.class)
                .hasRootCauseMessage("disk gone");
    }
}
