package com.github.diovanibmotta.spacebased.processingunit;

import com.github.diovanibmotta.spacebased.contracts.ReservationConfirmed;

public record ReservationOutcome(Status status, ReservationConfirmed reservation, int remainingSeats) {

    public enum Status {CONFIRMED, SOLD_OUT, UNKNOWN_EVENT}

    static ReservationOutcome confirmed(ReservationConfirmed reservation, int remaining) {
        return new ReservationOutcome(Status.CONFIRMED, reservation, remaining);
    }

    static ReservationOutcome soldOut() {
        return new ReservationOutcome(Status.SOLD_OUT, null, 0);
    }

    static ReservationOutcome unknownEvent() {
        return new ReservationOutcome(Status.UNKNOWN_EVENT, null, 0);
    }
}
