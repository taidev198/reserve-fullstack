package com.cmc.warehouse.domain.reservation.state;

import com.cmc.warehouse.domain.reservation.Reservation;
import com.cmc.warehouse.domain.reservation.ReservationStatus;

public final class ConfirmedState implements ReservationState {

    public static final ConfirmedState INSTANCE = new ConfirmedState();

    private ConfirmedState() {}

    @Override public ReservationStatus status() { return ReservationStatus.CONFIRMED; }

    @Override
    public void confirm(Reservation reservation) {
        throw new IllegalReservationTransitionException(status(), "confirm");
    }

    @Override
    public void cancel(Reservation reservation) {
        throw new IllegalReservationTransitionException(status(), "cancel");
    }
}
