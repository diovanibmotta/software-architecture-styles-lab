package com.github.diovanibmotta.spacebased.datawriter;

import com.github.diovanibmotta.spacebased.contracts.ReservationConfirmed;
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

class ReservationRepositoryTest {

    private ReservationRepository repository;

    @BeforeEach
    void setUp() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        repository = new ReservationRepository(JdbcClient.create(ds), new DataSourceTransactionManager(ds));
    }

    @Test
    void seedsEventAndAppliesReservation() {
        assertThat(repository.availableSeats("mega-concert-2026")).isEqualTo(1000);

        assertThat(repository.save(reservation("r1", 3))).isTrue();

        assertThat(repository.availableSeats("mega-concert-2026")).isEqualTo(997);
    }

    @Test
    void replayedMessageIsIgnored() {
        repository.save(reservation("r1", 2));

        assertThat(repository.save(reservation("r1", 2))).isFalse();

        assertThat(repository.availableSeats("mega-concert-2026")).isEqualTo(998);
    }

    private static ReservationConfirmed reservation(String id, int qty) {
        return new ReservationConfirmed(id, "mega-concert-2026", "customer", qty, Instant.now());
    }
}
