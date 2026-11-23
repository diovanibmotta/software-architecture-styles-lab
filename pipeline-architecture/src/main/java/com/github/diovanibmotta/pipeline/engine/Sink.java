package com.github.diovanibmotta.pipeline.engine;

/** Filter type "consumer": the end of a pipeline; it only has an inbound pipe. Named Sink to avoid clashing with java.util.function.Consumer. */
@FunctionalInterface
public interface Sink<I> {
    void consume(I input);
}
