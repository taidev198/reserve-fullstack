package com.cmc.warehouse.service.stock;

import com.cmc.warehouse.common.AppNumbers;
import com.cmc.warehouse.common.AppUtils;
import com.cmc.warehouse.repository.InventoryRepository;
import com.cmc.warehouse.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically repairs Redis counters from durable Postgres inventory.
 *
 * This closes the operational gap in dual-write windows where Redis and
 * Postgres can drift after partial failures.
 */
@Component
public class RedisInventoryReconciler {

    private static final Logger log = LoggerFactory.getLogger(RedisInventoryReconciler.class);

    private final InventoryRepository inventoryRepository;
    private final StringRedisTemplate redis;

    public RedisInventoryReconciler(InventoryRepository inventoryRepository, StringRedisTemplate redis) {
        this.inventoryRepository = inventoryRepository;
        this.redis = redis;
    }

    @Scheduled(fixedDelayString = "${app.stock.reconcile-interval-ms:60000}")
    public void reconcile() {
        int repaired = AppNumbers.ZERO;
        for (var inv : inventoryRepository.findAllWithProduct()) {
            String sku = inv.getProduct().getSku();
            int expectedStock = inv.getTotalQuantity();
            int expectedReserved = inv.getReservedQuantity();

            String stockKey = ReservationService.STOCK_PREFIX + sku;
            String reservedKey = ReservationService.RESERVED_PREFIX + sku;

            int liveStock = AppUtils.parseIntOrDefault(redis.opsForValue().get(stockKey), expectedStock);
            int liveReserved = AppUtils.parseIntOrDefault(redis.opsForValue().get(reservedKey), expectedReserved);

            if (liveStock != expectedStock || liveReserved != expectedReserved) {
                repaired++;
                log.warn("Reconciler repairing Redis counters for SKU {}: stock {}->{}, reserved {}->{}",
                        sku, liveStock, expectedStock, liveReserved, expectedReserved);
            }

            redis.opsForValue().set(stockKey, String.valueOf(expectedStock));
            redis.opsForValue().set(reservedKey, String.valueOf(expectedReserved));
        }
        if (repaired > AppNumbers.ZERO) {
            log.warn("Redis/Postgres reconciliation completed with {} repaired SKU(s)", repaired);
        } else {
            log.debug("Redis/Postgres reconciliation found no drift");
        }
    }
}
