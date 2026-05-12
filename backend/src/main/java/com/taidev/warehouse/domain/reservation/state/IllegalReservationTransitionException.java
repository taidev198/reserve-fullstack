package com.taidev.warehouse.domain.reservation.state;

import com.taidev.warehouse.domain.reservation.ReservationStatus;

public class IllegalReservationTransitionException extends RuntimeException {

    private final ReservationStatus from;
    private final String action;

    public IllegalReservationTransitionException(ReservationStatus from, String action) {
        super("Cannot %s reservation in state %s".formatted(action, from));
        this.from = from;
        this.action = action;
    }

    public ReservationStatus getFrom() { return from; }
    public String getAction() { return action; }
}
