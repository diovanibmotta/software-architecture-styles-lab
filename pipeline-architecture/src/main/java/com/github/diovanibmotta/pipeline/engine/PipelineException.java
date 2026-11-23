package com.github.diovanibmotta.pipeline.engine;

/** The producer failed: unlike a bad item, a broken source cannot be skipped. */
public class PipelineException extends RuntimeException {
    public PipelineException(String message, Throwable cause) {
        super(message, cause);
    }
}
