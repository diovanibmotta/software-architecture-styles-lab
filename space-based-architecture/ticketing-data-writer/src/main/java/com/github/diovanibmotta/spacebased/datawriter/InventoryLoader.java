package com.github.diovanibmotta.spacebased.datawriter;

import com.github.diovanibmotta.spacebased.contracts.EventInventory;
import com.github.diovanibmotta.spacebased.contracts.GridNames;
import com.hazelcast.core.HazelcastInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Data reader (reverse data pump): copies events from the database into the grid when they are missing,
 * e.g. on first boot or after the whole grid was restarted. {@code putIfAbsent} never overwrites live data.
 */
@Component
class InventoryLoader {

    private static final Logger log = LoggerFactory.getLogger(InventoryLoader.class);

    private final HazelcastInstance grid;
    private final ReservationRepository repository;

    InventoryLoader(HazelcastInstance grid, ReservationRepository repository) {
        this.grid = grid;
        this.repository = repository;
    }

    @Scheduled(fixedDelay = 10_000)
    void loadMissingEvents() {
        try {
            var inventory = grid.<String, EventInventory>getMap(GridNames.INVENTORY_MAP);
            for (EventInventory event : repository.findAllEvents()) {
                if (inventory.putIfAbsent(event.eventId(), event) == null) {
                    log.info("Loaded event {} into the grid with {} seats", event.eventId(), event.availableSeats());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not load inventory into the grid, will retry: {}", e.toString());
        }
    }
}
