package com.taidev.warehouse.domain.reservation.state;

import com.taidev.warehouse.domain.reservation.Reservation;
import com.taidev.warehouse.domain.reservation.ReservationStatus;

/**
 * State pattern — every {@link ReservationStatus} is backed by an
 * implementation of this interface, so transition rules live with
 * the state itself rather than as scattered {@code if/else} branches
 * inside service code.
 *
 * Each state knows:
 *   - which {@link ReservationStatus} it represents
 *   - which transitions are legal from here (confirm, cancel)
 *   - what side-effect the {@link Reservation} aggregate should apply
 *
 * Adding a new state (e.g. EXPIRED, PARTIALLY_FULFILLED) only requires
 * a new class and a registry entry — the rest of the codebase is closed
 * for modification.
 */
public interface ReservationState {

    ReservationStatus status();

    /**
     * Mark the reservation as confirmed.
     * Implementations must throw {@link IllegalReservationTransitionException}
     * if this state cannot be confirmed.
     */
    void confirm(Reservation reservation);

    /**
     * Mark the reservation as cancelled.
     * Implementations must throw {@link IllegalReservationTransitionException}
     * if this state cannot be cancelled.
     */
    void cancel(Reservation reservation);

    /** Whether {@link #confirm} would succeed from this state. */
    default boolean canConfirm() { return false; }

    /** Whether {@link #cancel} would succeed from this state. */
    default boolean canCancel() { return false; }
}
