package com.taidev.warehouse.repository;

import com.taidev.warehouse.domain.inventory.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Inventory persistence with SQL-level CAS for mutations.
 *
 * Every mutation is expressed as a single {@code UPDATE … WHERE …}
 * statement. The WHERE clause IS the compare-and-swap — Postgres
 * runs each UPDATE under a row lock, so the predicate (e.g.
 * {@code total - reserved >= :qty}) is checked against the same row
 * version that gets written. If the predicate fails the UPDATE
 * affects zero rows and the caller treats that as a domain failure
 * (insufficient stock / illegal bookkeeping). No SELECT-then-write
 * race window, no explicit row locks to manage.
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId")
    Optional<Inventory> findByProductId(@Param("productId") Long productId);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product ORDER BY i.product.sku")
    List<Inventory> findAllWithProduct();

    @EntityGraph(attributePaths = {"product"})
    Page<Inventory> findAllByOrderByProductSkuAsc(Pageable pageable);

    /**
     * Reserve {@code qty} units atomically. The {@code total_quantity
     * - reserved_quantity >= :qty} predicate is the CAS — if anyone
     * else moved the row first and there's no longer enough stock,
     * the row simply isn't matched and we return 0.
     */
    @Modifying
    @Query(value = """
            UPDATE inventory
               SET reserved_quantity = reserved_quantity + :qty,
                   version           = version + 1,
                   updated_at        = now()
             WHERE product_id = :productId
               AND (total_quantity - reserved_quantity) >= :qty
            """, nativeQuery = true)
    int reserveCas(@Param("productId") Long productId, @Param("qty") int qty);

    /**
     * Release {@code qty} previously-reserved units. Guarded so we
     * can never let {@code reserved_quantity} drift negative.
     */
    @Modifying
    @Query(value = """
            UPDATE inventory
               SET reserved_quantity = reserved_quantity - :qty,
                   version           = version + 1,
                   updated_at        = now()
             WHERE product_id = :productId
               AND reserved_quantity >= :qty
            """, nativeQuery = true)
    int releaseCas(@Param("productId") Long productId, @Param("qty") int qty);

    /**
     * Confirm consumption — stock has shipped. Decrements BOTH
     * total and reserved so the row invariants stay intact.
     */
    @Modifying
    @Query(value = """
            UPDATE inventory
               SET total_quantity    = total_quantity    - :qty,
                   reserved_quantity = reserved_quantity - :qty,
                   version           = version + 1,
                   updated_at        = now()
             WHERE product_id = :productId
               AND reserved_quantity >= :qty
               AND total_quantity    >= :qty
            """, nativeQuery = true)
    int consumeCas(@Param("productId") Long productId, @Param("qty") int qty);
}
