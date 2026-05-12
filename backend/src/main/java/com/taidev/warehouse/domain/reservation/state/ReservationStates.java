package com.taidev.warehouse.domain.reservation.state;

import com.taidev.warehouse.domain.reservation.ReservationStatus;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry mapping a persisted {@link ReservationStatus} enum back to
 * its behavioural {@link ReservationState} singleton. Using a map keeps
 * the lookup O(1) and makes adding a new state a single-line change.
 */
public final class ReservationStates {

    private static final Map<ReservationStatus, ReservationState> REGISTRY =
            new EnumMap<>(ReservationStatus.class);

    static {
        REGISTRY.put(ReservationStatus.PENDING,   PendingState.INSTANCE);
        REGISTRY.put(ReservationStatus.CONFIRMED, ConfirmedState.INSTANCE);
        REGISTRY.put(ReservationStatus.CANCELLED, CancelledState.INSTANCE);
    }

    private ReservationStates() {}

    public static ReservationState of(ReservationStatus status) {
        ReservationState state = REGISTRY.get(status);
        if (state == null) {
            throw new IllegalStateException("No state registered for " + status);
        }
        return state;
    }
}
