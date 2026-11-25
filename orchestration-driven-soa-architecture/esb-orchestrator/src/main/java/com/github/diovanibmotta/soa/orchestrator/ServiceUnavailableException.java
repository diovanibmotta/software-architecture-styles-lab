package com.github.diovanibmotta.soa.orchestrator;

/** A business service could not be reached or answered with an error (technical failure, not a business refusal). */
public class ServiceUnavailableException extends RuntimeException {

    private final String service;
    private final String orderId;

    public ServiceUnavailableException(String service, Throwable cause) {
        this(service, null, cause);
    }

    private ServiceUnavailableException(String service, String orderId, Throwable cause) {
        super(service + "-service unavailable", cause);
        this.service = service;
        this.orderId = orderId;
    }

    public ServiceUnavailableException forOrder(String orderId) {
        return new ServiceUnavailableException(service, orderId, getCause());
    }

    public String service() {
        return service;
    }

    public String orderId() {
        return orderId;
    }
}
