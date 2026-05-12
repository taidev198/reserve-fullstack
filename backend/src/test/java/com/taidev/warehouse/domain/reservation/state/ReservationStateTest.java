package com.taidev.warehouse.domain.reservation.state;

import com.taidev.warehouse.domain.reservation.Reservation;
import com.taidev.warehouse.domain.reservation.ReservationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationStateTest {

    @Test
    void pending_can_be_confirmed() {
        Reservation r = new Reservation("ORD-1");
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(r.currentState().canConfirm()).isTrue();
        assertThat(r.currentState().canCancel()).isTrue();

        r.confirm();

        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void pending_can_be_cancelled() {
        Reservation r = new Reservation("ORD-2");

        r.cancel();

        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void confirmed_cannot_be_confirmed_again() {
        Reservation r = new Reservation("ORD-3");
        r.confirm();

        assertThatThrownBy(r::confirm)
                .isInstanceOf(IllegalReservationTransitionException.class);
    }

    @Test
    void confirmed_cannot_be_cancelled() {
        Reservation r = new Reservation("ORD-4");
        r.confirm();

        assertThatThrownBy(r::cancel)
                .isInstanceOf(IllegalReservationTransitionException.class);
    }

    @Test
    void cancelled_is_terminal() {
        Reservation r = new Reservation("ORD-5");
        r.cancel();

        assertThatThrownBy(r::confirm)
                .isInstanceOf(IllegalReservationTransitionException.class);
        assertThatThrownBy(r::cancel)
                .isInstanceOf(IllegalReservationTransitionException.class);
    }
}
