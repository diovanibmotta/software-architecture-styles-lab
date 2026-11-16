package com.github.diovanibmotta.spacebased.datawriter;

import com.github.diovanibmotta.spacebased.contracts.GridNames;
import com.github.diovanibmotta.spacebased.contracts.ReservationConfirmed;
import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Data writer: drains the data pump and persists to the database.
 * Peek -> write -> poll gives at-least-once delivery (a crash between write and poll replays the message,
 * which the idempotent repository ignores).
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

    @Override
    public void start() {
        running = true;
        worker = new Thread(this::drain, "data-pump-consumer"); // non-daemon: keeps the app alive
        worker.start();
    }

    private void drain() {
        IQueue<ReservationConfirmed> pump = grid.getQueue(GridNames.DATA_PUMP_QUEUE);
        while (running) {
            try {
                ReservationConfirmed next = pump.peek();
                if (next == null) {
                    Thread.sleep(50);
                    continue;
                }
                boolean applied = repository.save(next);
                pump.poll();
                log.debug("Persisted reservation {} (duplicate={})", next.reservationId(), !applied);
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
