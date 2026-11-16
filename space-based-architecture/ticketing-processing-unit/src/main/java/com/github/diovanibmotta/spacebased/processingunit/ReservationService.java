package com.github.diovanibmotta.spacebased.processingunit;

import com.github.diovanibmotta.spacebased.contracts.EventInventory;
import com.github.diovanibmotta.spacebased.contracts.ReservationConfirmed;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.github.diovanibmotta.spacebased.contracts.GridNames.INVENTORY_MAP;

/**
 * Business logic of the processing unit. It never touches the database: reads and writes hit the data grid,
 * and the confirmed reservation is handed to the data pump to be persisted asynchronously.
 */
@Service
public class ReservationService {

    private final HazelcastInstance grid;

    public ReservationService(HazelcastInstance grid) {
        this.grid = grid;
    }

    public ReservationOutcome reserve(String eventId, String customerId, int quantity) {
        var candidate = new ReservationConfirmed(UUID.randomUUID().toString(), eventId, customerId, quantity, Instant.now());
        // stock decrement + outbox append are one atomic entry update; the data writer drains the outbox later
        int remaining = grid.<String, EventInventory>getMap(INVENTORY_MAP)
                .executeOnKey(eventId, new ReserveSeatsProcessor(candidate));

        if (remaining == ReserveSeatsProcessor.UNKNOWN_EVENT) {
            return ReservationOutcome.unknownEvent();
        }
        if (remaining == ReserveSeatsProcessor.SOLD_OUT) {
            return ReservationOutcome.soldOut();
        }
        return ReservationOutcome.confirmed(candidate, remaining);
    }

    /** Reservations accepted but not yet persisted by the data writer (all events). */
    public int pendingPersistence() {
        return grid.<String, EventInventory>getMap(INVENTORY_MAP).values().stream()
                .mapToInt(inv -> inv.pendingReservations().size()).sum();
    }

    public Optional<EventInventory> availability(String eventId) {
        return Optional.ofNullable(grid.<String, EventInventory>getMap(INVENTORY_MAP).get(eventId));
    }
}
