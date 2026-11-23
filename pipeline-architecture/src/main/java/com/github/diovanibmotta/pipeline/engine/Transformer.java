package com.github.diovanibmotta.pipeline.engine;

/** Filter type "transformer": takes one input, changes it, and forwards the result. Throwing sends the input to the dead letters. */
@FunctionalInterface
public interface Transformer<I, O> {
    O transform(I input);
}
