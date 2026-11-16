package com.github.diovanibmotta.spacebased.contracts;

import java.io.Serializable;

/** Seat inventory of one event. Lives in the in-memory data grid; the database is only a durable copy. */
public record EventInventory(String eventId, String name, int totalSeats, int availableSeats) implements Serializable {

    public EventInventory withAvailableSeats(int available) {
        return new EventInventory(eventId, name, totalSeats, available);
    }
}
