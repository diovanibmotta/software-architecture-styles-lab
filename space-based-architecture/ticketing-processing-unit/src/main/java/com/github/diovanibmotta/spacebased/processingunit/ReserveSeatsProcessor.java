package com.github.diovanibmotta.spacebased.processingunit;

import com.github.diovanibmotta.spacebased.contracts.EventInventory;
import com.github.diovanibmotta.spacebased.contracts.ReservationConfirmed;
import com.hazelcast.map.EntryProcessor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * Runs on the member that owns the key, holding that key's lock: the check-and-decrement is atomic
 * across the whole cluster without any distributed lock or database transaction.
 * Returns the remaining seats, or a negative code.
 */
public class ReserveSeatsProcessor implements EntryProcessor<String, EventInventory, Integer>, Serializable {

    public static final int SOLD_OUT = -1;
    public static final int UNKNOWN_EVENT = -2;

    @Serial
    private static final long serialVersionUID = 1L;

    private final ReservationConfirmed reservation;

    public ReserveSeatsProcessor(ReservationConfirmed reservation) {
        this.reservation = reservation;
    }

    @Override
    public Integer process(Map.Entry<String, EventInventory> entry) {
        EventInventory inventory = entry.getValue();
        if (inventory == null) {
            return UNKNOWN_EVENT;
        }
        if (inventory.availableSeats() < reservation.quantity()) {
            return SOLD_OUT;
        }
        // seats-- and outbox append in ONE entry write: atomic and replicated to the backups together
        EventInventory updated = inventory.withReservation(reservation);
        entry.setValue(updated);
        return updated.availableSeats();
    }
}
