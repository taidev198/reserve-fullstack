package com.taidev.warehouse.domain.reservation.state;

import com.taidev.warehouse.domain.reservation.Reservation;
import com.taidev.warehouse.domain.reservation.ReservationStatus;

public final class PendingState implements ReservationState {

    public static final PendingState INSTANCE = new PendingState();

    private PendingState() {}

    @Override public ReservationStatus status() { return ReservationStatus.PENDING; }
    @Override public boolean canConfirm() { return true; }
    @Override public boolean canCancel()  { return true; }

    @Override
    public void confirm(Reservation reservation) {
        reservation.transitionTo(ConfirmedState.INSTANCE);
    }

    @Override
    public void cancel(Reservation reservation) {
        reservation.transitionTo(CancelledState.INSTANCE);
    }
}
