package com.cmc.warehouse.domain.reservation.state;

import com.cmc.warehouse.domain.reservation.Reservation;
import com.cmc.warehouse.domain.reservation.ReservationStatus;

public final class CancelledState implements ReservationState {

    public static final CancelledState INSTANCE = new CancelledState();

    private CancelledState() {}

    @Override public ReservationStatus status() { return ReservationStatus.CANCELLED; }

    @Override
    public void confirm(Reservation reservation) {
        throw new IllegalReservationTransitionException(status(), "confirm");
    }

    @Override
    public void cancel(Reservation reservation) {
        throw new IllegalReservationTransitionException(status(), "cancel");
    }
}
