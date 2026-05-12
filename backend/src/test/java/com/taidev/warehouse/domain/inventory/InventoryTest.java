package com.taidev.warehouse.domain.inventory;

import com.taidev.warehouse.domain.product.Product;
import com.taidev.warehouse.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryTest {

    private static Inventory newInventory(int total) {
        return new Inventory(new Product("A100", "Wireless Mouse"), total);
    }

    @Test
    void reserves_within_available_stock() {
        Inventory inv = newInventory(100);

        inv.reserve(30);
        inv.reserve(40);

        assertThat(inv.getReservedQuantity()).isEqualTo(70);
        assertThat(inv.getAvailableQuantity()).isEqualTo(30);
    }

    @Test
    void refuses_to_oversell() {
        Inventory inv = newInventory(100);
        inv.reserve(70);

        assertThatThrownBy(() -> inv.reserve(50))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested 50")
                .hasMessageContaining("available 30");
    }

    @Test
    void release_returns_units_to_available_pool() {
        Inventory inv = newInventory(50);
        inv.reserve(20);

        inv.release(15);

        assertThat(inv.getReservedQuantity()).isEqualTo(5);
        assertThat(inv.getAvailableQuantity()).isEqualTo(45);
    }

    @Test
    void consume_reduces_total_and_reserved() {
        Inventory inv = newInventory(50);
        inv.reserve(20);

        inv.consume(20);

        assertThat(inv.getReservedQuantity()).isZero();
        assertThat(inv.getTotalQuantity()).isEqualTo(30);
        assertThat(inv.getAvailableQuantity()).isEqualTo(30);
    }

    @Test
    void cannot_release_more_than_reserved() {
        Inventory inv = newInventory(10);
        inv.reserve(5);

        assertThatThrownBy(() -> inv.release(6))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejects_non_positive_quantities() {
        Inventory inv = newInventory(10);
        assertThatThrownBy(() -> inv.reserve(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inv.reserve(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
