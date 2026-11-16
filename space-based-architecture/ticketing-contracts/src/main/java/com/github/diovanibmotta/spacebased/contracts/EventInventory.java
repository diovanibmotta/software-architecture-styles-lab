package com.github.diovanibmotta.spacebased.contracts;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Seat inventory of one event, living in the data grid. The database is only a durable copy.
 * <p>
 * It also carries the <b>outbox</b> of reservations not yet persisted ({@code pendingReservations}).
 * Decrementing the seats and appending the reservation happen in the SAME map entry, so they are one atomic,
 * replicated write: there is no window where a seat is sold but the reservation is lost on the way to the database.
 */
public record EventInventory(String eventId, String name, int totalSeats, int availableSeats,
                             List<ReservationConfirmed> pendingReservations) implements Serializable {

    public EventInventory(String eventId, String name, int totalSeats, int availableSeats) {
        this(eventId, name, totalSeats, availableSeats, List.of());
    }

    public EventInventory withReservation(ReservationConfirmed reservation) {
        List<ReservationConfirmed> pending = new ArrayList<>(pendingReservations);
        pending.add(reservation);
        return new EventInventory(eventId, name, totalSeats, availableSeats - reservation.quantity(), List.copyOf(pending));
    }

    /** Removes reservations the data writer has already persisted. */
    public EventInventory acknowledge(Set<String> persistedReservationIds) {
        List<ReservationConfirmed> pending = pendingReservations.stream()
                .filter(r -> !persistedReservationIds.contains(r.reservationId())).toList();
        return new EventInventory(eventId, name, totalSeats, availableSeats, pending);
    }
}
