package com.taidev.warehouse.service;

import com.taidev.warehouse.domain.product.Product;
import com.taidev.warehouse.domain.reservation.Reservation;
import com.taidev.warehouse.domain.reservation.ReservationStatus;
import com.taidev.warehouse.exception.ProductNotFoundException;
import com.taidev.warehouse.repository.ProductRepository;
import com.taidev.warehouse.service.command.ReservationLine;
import com.taidev.warehouse.validation.ReservationCreateValidator;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReservationFactoryTest {

    private ProductRepository productRepository;
    private ReservationFactory factory;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        factory = new ReservationFactory(
                productRepository,
                new ReservationCreateValidator(Validation.buildDefaultValidatorFactory().getValidator())
        );
    }

    @Test
    void builds_a_pending_reservation_with_wired_items() {
        when(productRepository.findBySkuIn(ArgumentMatchers.anyList()))
                .thenReturn(List.of(new Product("A100", "Mouse"), new Product("B200", "KB")));

        Reservation r = factory.create("ORD-1", List.of(
                new ReservationLine("A100", 2),
                new ReservationLine("B200", 1)
        ));

        assertThat(r.getOrderId()).isEqualTo("ORD-1");
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(r.getItems()).hasSize(2);
        assertThat(r.getItems().get(0).getReservation()).isSameAs(r);
    }

    @Test
    void rejects_unknown_sku() {
        when(productRepository.findBySkuIn(ArgumentMatchers.anyList()))
                .thenReturn(List.of(new Product("A100", "Mouse")));

        assertThatThrownBy(() -> factory.create("ORD-1", List.of(
                new ReservationLine("A100", 1),
                new ReservationLine("ZZZ", 1)
        ))).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void rejects_blank_order_id() {
        assertThatThrownBy(() -> factory.create("  ", List.of(new ReservationLine("A100", 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_empty_items() {
        assertThatThrownBy(() -> factory.create("ORD-1", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_non_positive_quantity() {
        assertThatThrownBy(() -> factory.create("ORD-1",
                List.of(new ReservationLine("A100", 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
