package com.taidev.warehouse.service;

import com.taidev.warehouse.BaseIntegrationTest;
import com.taidev.warehouse.domain.inventory.Inventory;
import com.taidev.warehouse.domain.reservation.Reservation;
import com.taidev.warehouse.domain.reservation.ReservationStatus;
import com.taidev.warehouse.domain.reservation.state.IllegalReservationTransitionException;
import com.taidev.warehouse.exception.DuplicateActiveReservationException;
import com.taidev.warehouse.exception.InsufficientStockException;
import com.taidev.warehouse.repository.InventoryRepository;
import com.taidev.warehouse.repository.ProductRepository;
import com.taidev.warehouse.service.command.ReservationLine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired ReservationService reservationService;
    @Autowired ProductRepository productRepository;
    @Autowired InventoryRepository inventoryRepository;

    private static final String SKU = "A100";

    @Test
    void reserve_then_confirm_consumes_stock() {
        Reservation r = reservationService.reserve("ORD-A",
                List.of(new ReservationLine(SKU, 10)));
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.PENDING);

        Reservation confirmed = reservationService.confirm(r.getId());
        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        Inventory inv = inventoryRepository
                .findByProductId(productRepository.findBySku(SKU).orElseThrow().getId())
                .orElseThrow();
        assertThat(inv.getTotalQuantity()).isEqualTo(90);
        assertThat(inv.getReservedQuantity()).isZero();
    }

    @Test
    void cancel_releases_reserved_stock() {
        Reservation r = reservationService.reserve("ORD-B",
                List.of(new ReservationLine(SKU, 30)));
        reservationService.cancel(r.getId());

        Inventory inv = inventoryRepository
                .findByProductId(productRepository.findBySku(SKU).orElseThrow().getId())
                .orElseThrow();
        assertThat(inv.getTotalQuantity()).isEqualTo(100);
        assertThat(inv.getReservedQuantity()).isZero();
    }

    @Test
    void confirming_a_cancelled_reservation_is_illegal() {
        Reservation r = reservationService.reserve("ORD-C",
                List.of(new ReservationLine(SKU, 5)));
        reservationService.cancel(r.getId());

        assertThatThrownBy(() -> reservationService.confirm(r.getId()))
                .isInstanceOf(IllegalReservationTransitionException.class);
    }

    @Test
    void rejects_overselling_at_the_boundary() {
        reservationService.reserve("ORD-D", List.of(new ReservationLine(SKU, 100)));

        assertThatThrownBy(() -> reservationService.reserve("ORD-E",
                List.of(new ReservationLine(SKU, 1))))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void same_order_id_and_same_lines_is_idempotent() {
        Reservation first = reservationService.reserve("ORD-IDEM-1",
                List.of(new ReservationLine(SKU, 10)));

        Reservation replay = reservationService.reserve("ORD-IDEM-1",
                List.of(new ReservationLine(SKU, 10)));

        assertThat(replay.getId()).isEqualTo(first.getId());

        Inventory inv = inventoryRepository
                .findByProductId(productRepository.findBySku(SKU).orElseThrow().getId())
                .orElseThrow();
        assertThat(inv.getReservedQuantity()).isEqualTo(10);
    }

    @Test
    void same_order_id_with_different_lines_is_rejected() {
        reservationService.reserve("ORD-IDEM-2",
                List.of(new ReservationLine(SKU, 10)));

        assertThatThrownBy(() -> reservationService.reserve("ORD-IDEM-2",
                List.of(new ReservationLine(SKU, 11))))
                .isInstanceOf(DuplicateActiveReservationException.class);
    }

    /**
     * Concurrency contract: when 50 threads each try to reserve 5 units
     * against a 100-unit pool, exactly 20 reservations must succeed and
     * 30 must fail — never 21, never 19. This proves the Redis Lua
     * atomicity plus the Postgres SQL CAS in
     * {@link ReservationService#reserve} jointly eliminate the
     * read-modify-write race.
     */
    @Test
    void concurrent_reservations_never_oversell() throws Exception {
        final int threads = 50;
        final int qtyPerRequest = 5;
        // expected successes = 100 / 5 = 20

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        AtomicInteger otherFailures = new AtomicInteger();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    reservationService.reserve("ORD-CONC-" + idx,
                            List.of(new ReservationLine(SKU, qtyPerRequest)));
                    successes.incrementAndGet();
                } catch (InsufficientStockException e) {
                    insufficient.incrementAndGet();
                } catch (Exception e) {
                    otherFailures.incrementAndGet();
                }
                return null;
            }));
        }

        start.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        Inventory inv = inventoryRepository
                .findByProductId(productRepository.findBySku(SKU).orElseThrow().getId())
                .orElseThrow();

        assertThat(otherFailures.get())
                .as("no unexpected failures (deadlocks, lock timeouts, etc.)")
                .isZero();
        assertThat(successes.get())
                .as("exactly 20 reservations should succeed")
                .isEqualTo(20);
        assertThat(insufficient.get()).isEqualTo(threads - 20);
        assertThat(inv.getReservedQuantity())
                .as("no oversell")
                .isEqualTo(100);
        assertThat(inv.getReservedQuantity())
                .isLessThanOrEqualTo(inv.getTotalQuantity());
    }
}
