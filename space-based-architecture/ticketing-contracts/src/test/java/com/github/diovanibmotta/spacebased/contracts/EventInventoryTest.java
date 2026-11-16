package com.github.diovanibmotta.spacebased.contracts;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EventInventoryTest {

    private static ReservationConfirmed reservation(String id, int qty) {
        return new ReservationConfirmed(id, "e", "c", qty, Instant.now());
    }

    @Test
    void reservationDecrementsSeatsAndJoinsOutboxInOneStep() {
        var inv = new EventInventory("e", "Event", 10, 10).withReservation(reservation("r1", 3));

        assertThat(inv.availableSeats()).isEqualTo(7);
        assertThat(inv.pendingReservations()).extracting(ReservationConfirmed::reservationId).containsExactly("r1");
    }

    @Test
    void acknowledgeRemovesOnlyPersistedOnesAndKeepsSeatCount() {
        var inv = new EventInventory("e", "Event", 10, 10)
                .withReservation(reservation("r1", 1)).withReservation(reservation("r2", 2));

        var acked = inv.acknowledge(Set.of("r1"));

        assertThat(acked.pendingReservations()).extracting(ReservationConfirmed::reservationId).containsExactly("r2");
        assertThat(acked.availableSeats()).isEqualTo(7);
    }
}
