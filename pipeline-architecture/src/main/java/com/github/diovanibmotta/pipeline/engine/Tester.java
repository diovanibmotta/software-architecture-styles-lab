package com.github.diovanibmotta.pipeline.engine;

import java.util.Optional;

/** Filter type "tester": forwards the input untouched, or rejects it with a reason. */
@FunctionalInterface
public interface Tester<T> {
    /** @return empty to accept the input, or the rejection reason. */
    Optional<String> check(T input);
}
