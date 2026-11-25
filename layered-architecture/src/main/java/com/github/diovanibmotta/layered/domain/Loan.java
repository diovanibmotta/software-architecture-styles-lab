package com.github.diovanibmotta.layered.domain;

import java.time.LocalDate;

public record Loan(long id, String isbn, String memberId, LocalDate borrowedOn, LocalDate dueOn,
                   LocalDate returnedOn, Integer fineCents) {

    public boolean isActive() {
        return returnedOn == null;
    }

    public boolean isOverdue(LocalDate today) {
        return isActive() && today.isAfter(dueOn);
    }
}
