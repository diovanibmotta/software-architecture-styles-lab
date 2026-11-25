package com.github.diovanibmotta.microservices.order;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DownstreamTest {

    StubServer stub;
    Downstream downstream;

    @BeforeEach
    void start() {
        stub = new StubServer();
        // 300 ms timeout, opens after 3 failed calls, stays open 400 ms
        downstream = new Downstream("stub", stub.url(), new Downstream.Settings(300, 4, 3, 50, 400));
    }

    @AfterEach
    void stop() {
        stub.close();
    }

    private String get() {
        return downstream.call(c -> c.get().uri("/x").retrieve().body(String.class));
    }

    @Test
    void healthyServiceAnswers() {
        assertThat(get()).isEqualTo("{}");
        assertThat(downstream.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void slowServiceTimesOut() {
        stub.delayMs = 1_000;
        assertThatThrownBy(this::get).isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void breakerOpensAfterRepeatedFailuresAndFailsFastWithoutCallingTheService() {
        stub.status.set(500);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(this::get).isInstanceOf(DependencyUnavailableException.class);
        }
        assertThat(downstream.state()).isEqualTo(CircuitBreaker.State.OPEN);

        int hits = stub.requests.size();
        assertThatThrownBy(this::get).isInstanceOf(DependencyUnavailableException.class);
        assertThat(stub.requests).hasSize(hits);   // short-circuited: the service did not see the call
    }

    @Test
    void breakerRecoversThroughHalfOpenOnceTheServiceIsBack() throws Exception {
        stub.status.set(500);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(this::get).isInstanceOf(DependencyUnavailableException.class);
        }
        stub.status.set(200);
        Thread.sleep(500);                          // wait-duration-in-open-state elapsed

        assertThat(get()).isEqualTo("{}");          // trial call succeeds
        assertThat(downstream.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void clientErrorsAreBusinessAnswersNotFailures() {
        stub.status.set(404);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(this::get).isNotInstanceOf(DependencyUnavailableException.class);
        }
        assertThat(downstream.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
