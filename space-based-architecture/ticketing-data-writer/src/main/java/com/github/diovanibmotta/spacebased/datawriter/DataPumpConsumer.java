package com.github.diovanibmotta.spacebased.datawriter;

import com.github.diovanibmotta.spacebased.contracts.AcknowledgeProcessor;
import com.github.diovanibmotta.spacebased.contracts.EventInventory;
import com.github.diovanibmotta.spacebased.contracts.GridNames;
import com.github.diovanibmotta.spacebased.contracts.ReservationConfirmed;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Data writer: drains the outbox that lives inside each {@link EventInventory} and persists it to the database.
 * Order of operations: read outbox -> commit to DB -> acknowledge in the grid. A crash after the commit and
 * before the acknowledge replays the same reservations, which the idempotent repository ignores (at-least-once).
 */
@Component
class DataPumpConsumer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DataPumpConsumer.class);

    private final HazelcastInstance grid;
    private final ReservationRepository repository;
    private volatile boolean running;
    private Thread worker;

    DataPumpConsumer(HazelcastInstance grid, ReservationRepository repository) {
        this.grid = grid;
        this.repository = repository;
    }

    /** One pass over every event; returns how many reservations were handled. */
    int drainOnce() {
        IMap<String, EventInventory> inventory = grid.getMap(GridNames.INVENTORY_MAP);
        int handled = 0;
        for (String eventId : inventory.keySet()) {
            EventInventory current = inventory.get(eventId);
            List<ReservationConfirmed> pending = current == null ? List.of() : current.pendingReservations();
            if (pending.isEmpty()) {
                continue;
            }
            for (ReservationConfirmed reservation : pending) {
                boolean applied = repository.save(reservation);
                log.debug("Persisted reservation {} (duplicate={})", reservation.reservationId(), !applied);
            }
            Set<String> ids = pending.stream().map(ReservationConfirmed::reservationId).collect(Collectors.toSet());
            inventory.executeOnKey(eventId, new AcknowledgeProcessor(ids));
            handled += pending.size();
        }
        return handled;
    }

    @Override
    public void start() {
        running = true;
        worker = new Thread(this::loop, "data-pump-consumer"); // non-daemon: keeps the app alive
        worker.start();
    }

    private void loop() {
        while (running) {
            try {
                if (drainOnce() == 0) {
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.warn("Data pump write failed, retrying in 1s: {}", e.toString());
                sleepQuietly(1000);
            }
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
