package com.github.diovanibmotta.spacebased.processingunit;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cluster.Member;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.github.diovanibmotta.spacebased.contracts.GridNames.DATA_PUMP_QUEUE;

@RestController
class ReservationController {

    static final String SERVED_BY = "X-Served-By";

    private final ReservationService service;
    private final HazelcastInstance grid;
    private final String nodeName;

    ReservationController(ReservationService service, HazelcastInstance grid,
                          @Value("${grid.node-name:${HOSTNAME:local}}") String nodeName) {
        this.service = service;
        this.grid = grid;
        this.nodeName = nodeName;
    }

    record ReserveRequest(@NotBlank String customerId, @Min(1) @Max(4) int quantity) {
    }

    @PostMapping("/events/{eventId}/reservations")
    ResponseEntity<?> reserve(@PathVariable String eventId, @Valid @RequestBody ReserveRequest request) {
        ReservationOutcome outcome = service.reserve(eventId, request.customerId(), request.quantity());
        return switch (outcome.status()) {
            case CONFIRMED -> reply(HttpStatus.CREATED, Map.of(
                    "reservationId", outcome.reservation().reservationId(),
                    "quantity", outcome.reservation().quantity(),
                    "remainingSeats", outcome.remainingSeats()));
            case SOLD_OUT -> reply(HttpStatus.CONFLICT, Map.of("error", "SOLD_OUT"));
            case UNKNOWN_EVENT -> reply(HttpStatus.NOT_FOUND, Map.of("error", "UNKNOWN_EVENT"));
        };
    }

    @GetMapping("/events/{eventId}/availability")
    ResponseEntity<?> availability(@PathVariable String eventId) {
        return service.availability(eventId)
                .<ResponseEntity<?>>map(inv -> reply(HttpStatus.OK, inv))
                .orElseGet(() -> reply(HttpStatus.NOT_FOUND, Map.of("error", "UNKNOWN_EVENT")));
    }

    /** Grid topology and backlog of the data pump (useful to watch the async persistence catching up). */
    @GetMapping("/grid")
    Map<String, Object> grid() {
        return Map.of(
                "node", nodeName,
                "members", grid.getCluster().getMembers().stream().map(Member::getAddress).map(Object::toString).toList(),
                "dataPumpBacklog", grid.getQueue(DATA_PUMP_QUEUE).size());
    }

    private <T> ResponseEntity<T> reply(HttpStatus status, T body) {
        return ResponseEntity.status(status).header(SERVED_BY, nodeName).body(body);
    }
}
