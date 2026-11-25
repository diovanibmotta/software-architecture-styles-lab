package com.github.diovanibmotta.microservices.order;

/** A downstream service timed out, failed, or its circuit breaker is open. Mapped to 503. */
public class DependencyUnavailableException extends RuntimeException {
    DependencyUnavailableException(String dependency, Throwable cause) {
        super(dependency + " unavailable", cause);
    }
}
