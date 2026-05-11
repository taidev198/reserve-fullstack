package com.cmc.warehouse.domain.reservation;

import com.cmc.warehouse.domain.reservation.state.ReservationState;
import com.cmc.warehouse.domain.reservation.state.ReservationStates;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reservation aggregate root.
 *
 * Lifecycle is delegated to the {@link ReservationState} interface
 * (State pattern) — callers ask the reservation to {@link #confirm()}
 * or {@link #cancel()} and the current state decides whether to allow
 * the transition.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ReservationItem> items = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Reservation() {}

    /** Reserved for {@link com.cmc.warehouse.service.ReservationFactory}. */
    public Reservation(String orderId) {
        this.orderId = orderId;
        this.status  = ReservationStatus.PENDING;
    }

    /** Reserved for {@link com.cmc.warehouse.service.ReservationFactory}. */
    public void addItem(ReservationItem item) {
        this.items.add(item);
    }

    public void confirm() {
        currentState().confirm(this);
    }

    public void cancel() {
        currentState().cancel(this);
    }

    /**
     * Package-private hook used by {@link ReservationState} implementations
     * to actually flip the persisted status. Kept on the aggregate so the
     * invariant "status only changes through a state object" is preserved.
     */
    public void transitionTo(ReservationState newState) {
        this.status = newState.status();
        this.updatedAt = Instant.now();
    }

    public ReservationState currentState() {
        return ReservationStates.of(status);
    }

    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public ReservationStatus getStatus() { return status; }
    public List<ReservationItem> getItems() { return Collections.unmodifiableList(items); }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
