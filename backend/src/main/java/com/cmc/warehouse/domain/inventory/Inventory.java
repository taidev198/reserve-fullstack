package com.cmc.warehouse.domain.inventory;

import com.cmc.warehouse.common.AppMessages;
import com.cmc.warehouse.domain.product.Product;
import com.cmc.warehouse.exception.InsufficientStockException;
import com.cmc.warehouse.validation.InventoryValidator;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Per-SKU stock aggregate. Encapsulates the invariant
 *   0 <= reserved_quantity <= total_quantity
 * so the rule can never be violated by callers — every domain-level
 * mutation goes through {@link #reserve(int)}, {@link #release(int)}
 * or {@link #consume(int)}, all of which throw on invalid transitions.
 *
 * Note: the JPA strategy doesn't actually call these helpers — it
 * runs SQL-CAS UPDATEs that re-express the same invariants in the
 * WHERE clause. These methods stay because they're useful for
 * isolated unit tests of the aggregate's rules.
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Inventory() {}

    public Inventory(Product product, int totalQuantity) {
        InventoryValidator.validateInitialTotalQuantity(totalQuantity);
        this.product = product;
        this.totalQuantity = totalQuantity;
        this.reservedQuantity = 0;
    }

    public int getAvailableQuantity() {
        return totalQuantity - reservedQuantity;
    }

    /** Holds {@code quantity} units against future fulfilment. */
    public void reserve(int quantity) {
        requirePositive(quantity);
        if (quantity > getAvailableQuantity()) {
            throw new InsufficientStockException(product.getSku(), quantity, getAvailableQuantity());
        }
        this.reservedQuantity += quantity;
        this.updatedAt = Instant.now();
    }

    /** Releases previously-reserved units back to the available pool. */
    public void release(int quantity) {
        requirePositive(quantity);
        if (quantity > reservedQuantity) {
            throw new IllegalStateException(AppMessages.CANNOT_RELEASE_MORE_THAN_RESERVED);
        }
        this.reservedQuantity -= quantity;
        this.updatedAt = Instant.now();
    }

    /** Confirms a reservation: stock leaves the warehouse permanently. */
    public void consume(int quantity) {
        requirePositive(quantity);
        if (quantity > reservedQuantity) {
            throw new IllegalStateException(AppMessages.CANNOT_CONSUME_MORE_THAN_RESERVED);
        }
        this.reservedQuantity -= quantity;
        this.totalQuantity   -= quantity;
        this.updatedAt = Instant.now();
    }

    private static void requirePositive(int quantity) {
        InventoryValidator.validatePositiveQuantity(quantity);
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public int getTotalQuantity() { return totalQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
