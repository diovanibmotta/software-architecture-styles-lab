package com.github.diovanibmotta.spacebased.contracts;

import java.io.Serializable;
import java.time.Instant;

/** Message sent through the data pump (queue) after seats were reserved in the grid. */
public record ReservationConfirmed(String reservationId, String eventId, String customerId, int quantity,
                                   Instant confirmedAt) implements Serializable {
}
