package com.github.diovanibmotta.microservices.order;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Function;

/**
 * One remote dependency, protected by a timeout (the HTTP read timeout) and a circuit breaker. Timeouts, connection
 * errors and 5xx count as failures; 4xx are business answers and are ignored by the breaker. When the breaker is open
 * calls fail immediately instead of piling up threads on a dead service.
 */
public class Downstream {

    public record Settings(long timeoutMs, int window, int minCalls, float failureRate, long openMs) {
    }

    private final String name;
    private final RestClient client;
    private final CircuitBreaker breaker;

    public Downstream(String name, String baseUrl, Settings settings) {
        this.name = name;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(settings.timeoutMs())).build());
        factory.setReadTimeout(Duration.ofMillis(settings.timeoutMs()));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.breaker = CircuitBreaker.of(name, CircuitBreakerConfig.custom()
                .slidingWindowSize(settings.window())
                .minimumNumberOfCalls(settings.minCalls())
                .failureRateThreshold(settings.failureRate())
                .waitDurationInOpenState(Duration.ofMillis(settings.openMs()))
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .ignoreExceptions(HttpClientErrorException.class)
                .build());
    }

    public <T> T call(Function<RestClient, T> request) {
        try {
            return breaker.executeSupplier(() -> request.apply(client));
        } catch (CallNotPermittedException | ResourceAccessException | HttpServerErrorException e) {
            throw new DependencyUnavailableException(name, e);
        }
    }

    CircuitBreaker.State state() {
        return breaker.getState();
    }
}
