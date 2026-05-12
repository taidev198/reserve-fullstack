package com.taidev.warehouse.domain.reservation;

import com.taidev.warehouse.domain.product.Product;
import jakarta.persistence.*;

@Entity
@Table(name = "reservation_items")
public class ReservationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    protected ReservationItem() {}

    public ReservationItem(Reservation reservation, Product product, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        this.reservation = reservation;
        this.product = product;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
}
