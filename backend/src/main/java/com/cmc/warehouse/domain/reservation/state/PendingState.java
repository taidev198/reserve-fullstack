package com.cmc.warehouse.domain.reservation.state;

import com.cmc.warehouse.domain.reservation.Reservation;
import com.cmc.warehouse.domain.reservation.ReservationStatus;

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
