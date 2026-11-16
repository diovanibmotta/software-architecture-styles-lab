package com.github.diovanibmotta.spacebased.processingunit;

import com.github.diovanibmotta.spacebased.contracts.EventInventory;
import com.github.diovanibmotta.spacebased.contracts.ReservationConfirmed;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.github.diovanibmotta.spacebased.contracts.GridNames.DATA_PUMP_QUEUE;
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
        int remaining = grid.<String, EventInventory>getMap(INVENTORY_MAP)
                .executeOnKey(eventId, new ReserveSeatsProcessor(quantity));

        if (remaining == ReserveSeatsProcessor.UNKNOWN_EVENT) {
            return ReservationOutcome.unknownEvent();
        }
        if (remaining == ReserveSeatsProcessor.SOLD_OUT) {
            return ReservationOutcome.soldOut();
        }
        var confirmed = new ReservationConfirmed(UUID.randomUUID().toString(), eventId, customerId, quantity, Instant.now());
        grid.<ReservationConfirmed>getQueue(DATA_PUMP_QUEUE).offer(confirmed); // data pump (async persistence)
        return ReservationOutcome.confirmed(confirmed, remaining);
    }

    public Optional<EventInventory> availability(String eventId) {
        return Optional.ofNullable(grid.<String, EventInventory>getMap(INVENTORY_MAP).get(eventId));
    }
}
