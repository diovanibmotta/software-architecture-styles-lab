package com.github.diovanibmotta.spacebased.processingunit;

import com.github.diovanibmotta.spacebased.contracts.EventInventory;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.github.diovanibmotta.spacebased.contracts.GridNames.INVENTORY_MAP;
import static org.assertj.core.api.Assertions.assertThat;

/** Starts two real grid members in the same JVM and attacks them concurrently. */
class ReservationGridTest {

    private static final String EVENT = "concert";
    private static final int SEATS = 100;

    private HazelcastInstance unit1;
    private HazelcastInstance unit2;

    @BeforeEach
    void startGrid() {
        List<String> members = List.of("127.0.0.1:5801", "127.0.0.1:5802");
        unit1 = Hazelcast.newHazelcastInstance(GridConfigFactory.create(5801, members, 1));
        unit2 = Hazelcast.newHazelcastInstance(GridConfigFactory.create(5802, members, 1));
        unit1.<String, EventInventory>getMap(INVENTORY_MAP).put(EVENT, new EventInventory(EVENT, "Concert", SEATS, SEATS));
    }

    @AfterEach
    void stopGrid() {
        Hazelcast.shutdownAll();
    }

    @Test
    void neverOversellsUnderConcurrentLoadSpreadAcrossUnits() throws Exception {
        assertThat(unit1.getCluster().getMembers()).hasSize(2);
        var service1 = new ReservationService(unit1);
        var service2 = new ReservationService(unit2);

        ExecutorService pool = Executors.newFixedThreadPool(64);
        // 400 buyers fight for 100 seats, alternating between the two processing units
        List<Future<ReservationOutcome>> results = IntStream.range(0, 400)
                .mapToObj(i -> pool.submit(() -> (i % 2 == 0 ? service1 : service2).reserve(EVENT, "customer-" + i, 1)))
                .toList();
        long confirmed = 0;
        for (var f : results) {
            if (f.get(30, TimeUnit.SECONDS).status() == ReservationOutcome.Status.CONFIRMED) {
                confirmed++;
            }
        }
        pool.shutdown();

        assertThat(confirmed).isEqualTo(SEATS);
        assertThat(service1.availability(EVENT)).get().extracting(EventInventory::availableSeats).isEqualTo(0);
        assertThat(service2.pendingPersistence()).isEqualTo(SEATS); // every sold seat has its outbox entry
    }

    @Test
    void survivesLossOfOneUnitWithoutLosingInventory() {
        var service2 = new ReservationService(unit2);
        new ReservationService(unit1).reserve(EVENT, "a", 3);

        unit1.shutdown(); // simulates a crashed processing unit

        assertThat(service2.availability(EVENT)).get().extracting(EventInventory::availableSeats).isEqualTo(SEATS - 3);
        assertThat(service2.reserve(EVENT, "b", 1).status()).isEqualTo(ReservationOutcome.Status.CONFIRMED);
        assertThat(service2.pendingPersistence()).isEqualTo(2); // outbox survived the crash together with the stock
    }

    @Test
    void rejectsUnknownEventAndSoldOut() {
        var service = new ReservationService(unit1);
        assertThat(service.reserve("nope", "a", 1).status()).isEqualTo(ReservationOutcome.Status.UNKNOWN_EVENT);
        assertThat(service.reserve(EVENT, "a", 4).status()).isEqualTo(ReservationOutcome.Status.CONFIRMED);
        unit1.<String, EventInventory>getMap(INVENTORY_MAP).put(EVENT, new EventInventory(EVENT, "Concert", SEATS, 1));
        assertThat(service.reserve(EVENT, "b", 2).status()).isEqualTo(ReservationOutcome.Status.SOLD_OUT);
    }
}
