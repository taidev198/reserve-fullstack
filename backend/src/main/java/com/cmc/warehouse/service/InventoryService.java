package com.cmc.warehouse.service;

import com.cmc.warehouse.repository.InventoryRepository;
import com.cmc.warehouse.service.stock.StockSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-side projection for the dashboard.
 *
 * Catalog metadata + durable totals come from Postgres; the live
 * {@code reserved_quantity} is read from Redis so the UI sees holds
 * the moment they happen (Postgres also tracks them, but the Redis
 * value is what changes first on every reserve). If Redis is missing
 * a key — e.g. the SKU was just added — we fall back to the Postgres
 * value, which is always correct, just possibly slightly stale on
 * very recent activity.
 */
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StringRedisTemplate redis;

    public InventoryService(InventoryRepository inventoryRepository,
                            StringRedisTemplate redis) {
        this.inventoryRepository = inventoryRepository;
        this.redis = redis;
    }

    public List<StockSnapshot> listAll() {
        var inventories = inventoryRepository.findAllWithProduct();
        List<StockSnapshot> result = new ArrayList<>(inventories.size());
        for (var inv : inventories) {
            String sku = inv.getProduct().getSku();
            String liveReserved = redis.opsForValue().get(ReservationService.RESERVED_PREFIX + sku);
            int reserved = liveReserved != null
                    ? Integer.parseInt(liveReserved)
                    : inv.getReservedQuantity();
            result.add(new StockSnapshot(
                    sku,
                    inv.getProduct().getName(),
                    inv.getTotalQuantity(),
                    reserved,
                    inv.getVersion()));
        }
        return result;
    }

    public Page<StockSnapshot> listPage(Pageable pageable) {
        var page = inventoryRepository.findAllByOrderByProductSkuAsc(pageable);
        List<StockSnapshot> content = new ArrayList<>(page.getNumberOfElements());
        for (var inv : page.getContent()) {
            String sku = inv.getProduct().getSku();
            String liveReserved = redis.opsForValue().get(ReservationService.RESERVED_PREFIX + sku);
            int reserved = liveReserved != null
                    ? Integer.parseInt(liveReserved)
                    : inv.getReservedQuantity();
            content.add(new StockSnapshot(
                    sku,
                    inv.getProduct().getName(),
                    inv.getTotalQuantity(),
                    reserved,
                    inv.getVersion()));
        }
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }
}
