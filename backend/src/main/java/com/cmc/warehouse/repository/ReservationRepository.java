package com.cmc.warehouse.repository;

import com.cmc.warehouse.domain.reservation.Reservation;
import com.cmc.warehouse.domain.reservation.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdWithItems(Long id);

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("SELECT r FROM Reservation r ORDER BY r.createdAt DESC")
    List<Reservation> findAllWithItems();

    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query(
            value = "SELECT r FROM Reservation r ORDER BY r.createdAt DESC",
            countQuery = "SELECT count(r) FROM Reservation r"
    )
    Page<Reservation> findPageWithItems(Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Reservation> findFirstByOrderIdAndStatusOrderByCreatedAtDesc(String orderId, ReservationStatus status);

    @Query("""
            SELECT r.id
            FROM Reservation r
            WHERE r.status = :status
              AND r.createdAt < :cutoff
            ORDER BY r.createdAt ASC
            """)
    List<Long> findIdsByStatusAndCreatedAtBefore(
            ReservationStatus status,
            Instant cutoff,
            Pageable pageable
    );
}
