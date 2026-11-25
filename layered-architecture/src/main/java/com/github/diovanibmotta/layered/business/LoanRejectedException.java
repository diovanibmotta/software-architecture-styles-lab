package com.github.diovanibmotta.layered.business;

/** A business rule said no. The presentation layer decides how that looks on the wire. */
public class LoanRejectedException extends RuntimeException {

    public enum Reason { NO_COPIES_AVAILABLE, LOAN_LIMIT_REACHED, HAS_OVERDUE_LOANS, ALREADY_RETURNED }

    private final Reason reason;

    public LoanRejectedException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
