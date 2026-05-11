package com.cmc.warehouse.api.dto;

import com.cmc.warehouse.domain.reservation.ReservationStatus;

import java.time.Instant;
import java.util.List;

public final class ReservationResponses {

    private ReservationResponses() {}

    public record ReservationView(
            Long id,
            String orderId,
            ReservationStatus status,
            boolean canConfirm,
            boolean canCancel,
            List<ItemView> items,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record ItemView(String sku, String name, int quantity) {}

    public record InventoryView(
            String sku,
            String name,
            int totalQuantity,
            int reservedQuantity,
            int availableQuantity,
            long version
    ) {}
}
