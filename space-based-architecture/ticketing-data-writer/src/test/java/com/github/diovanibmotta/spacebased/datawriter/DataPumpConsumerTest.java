package com.github.diovanibmotta.spacebased.datawriter;

import com.github.diovanibmotta.spacebased.contracts.EventInventory;
import com.github.diovanibmotta.spacebased.contracts.GridNames;
import com.github.diovanibmotta.spacebased.contracts.ReservationConfirmed;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DataPumpConsumerTest {

    private static final String EVENT = "mega-concert-2026";

    private HazelcastInstance grid;
    private ReservationRepository repository;
    private DataPumpConsumer consumer;

    @BeforeEach
    void setUp() {
        Config config = new Config().setClusterName("writer-test-" + UUID.randomUUID());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.setProperty("hazelcast.phone.home.enabled", "false");
        grid = Hazelcast.newHazelcastInstance(config);

        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        repository = new ReservationRepository(JdbcClient.create(ds), new DataSourceTransactionManager(ds));
        consumer = new DataPumpConsumer(grid, repository);
    }

    @AfterEach
    void tearDown() {
        grid.shutdown();
    }

    private EventInventory inventoryWith(ReservationConfirmed... reservations) {
        var inv = new EventInventory(EVENT, "Concert", 1000, 1000);
        for (var r : reservations) {
            inv = inv.withReservation(r);
        }
        grid.<String, EventInventory>getMap(GridNames.INVENTORY_MAP).put(EVENT, inv);
        return inv;
    }

    private static ReservationConfirmed reservation(String id, int qty) {
        return new ReservationConfirmed(id, EVENT, "customer", qty, Instant.now());
    }

    private EventInventory current() {
        return grid.<String, EventInventory>getMap(GridNames.INVENTORY_MAP).get(EVENT);
    }

    @Test
    void persistsOutboxThenAcknowledgesItInTheGrid() {
        inventoryWith(reservation("r1", 2), reservation("r2", 3));

        assertThat(consumer.drainOnce()).isEqualTo(2);

        assertThat(repository.availableSeats(EVENT)).isEqualTo(995);
        assertThat(current().pendingReservations()).isEmpty();
        assertThat(current().availableSeats()).isEqualTo(995); // grid stock untouched by the ack
    }

    @Test
    void replayAfterCrashBetweenCommitAndAckDoesNotDoubleCount() {
        var r1 = reservation("r1", 2);
        inventoryWith(r1);
        repository.save(r1); // writer committed, then "crashed" before acknowledging

        consumer.drainOnce(); // restarted writer sees r1 again

        assertThat(repository.availableSeats(EVENT)).isEqualTo(998);
        assertThat(current().pendingReservations()).isEmpty();
    }
}
