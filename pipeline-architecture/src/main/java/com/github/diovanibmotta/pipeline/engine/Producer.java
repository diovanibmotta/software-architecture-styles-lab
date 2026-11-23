package com.github.diovanibmotta.pipeline.engine;

import java.util.function.Consumer;

/** Filter type "producer": the starting point of a pipeline; it only has an outbound pipe. */
@FunctionalInterface
public interface Producer<O> {
    void produce(Consumer<O> emit) throws Exception;
}
