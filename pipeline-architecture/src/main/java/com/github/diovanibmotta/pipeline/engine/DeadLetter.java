package com.github.diovanibmotta.pipeline.engine;

/** An item that a filter refused or failed on; the pipeline keeps flowing without it. */
public record DeadLetter(String stage, Object item, String reason) {
}
