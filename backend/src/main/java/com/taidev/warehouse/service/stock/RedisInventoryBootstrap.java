package com.taidev.warehouse.service.stock;

import com.taidev.warehouse.repository.InventoryRepository;
import com.taidev.warehouse.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * On startup, copy each SKU's durable total / reserved from Postgres
 * into Redis. Uses the idempotent {@code init_sku.lua} script so an
 * existing Redis state is left alone — only new SKUs (or a fresh
 * Redis) get seeded.
 *
 * In production a periodic reconciler would also run, comparing
 * Redis vs Postgres counts and reporting drift. Out of scope here.
 */
@Configuration
public class RedisInventoryBootstrap {

    private static final Logger log = LoggerFactory.getLogger(RedisInventoryBootstrap.class);

    @Bean
    public ApplicationRunner seedRedisInventory(InventoryRepository inventoryRepository,
                                                StringRedisTemplate redis,
                                                RedisScript<Long> initSkuScript) {
        return args -> {
            int created = 0;
            int existing = 0;
            for (var inv : inventoryRepository.findAllWithProduct()) {
                String sku = inv.getProduct().getSku();
                Long result = redis.execute(
                        initSkuScript,
                        List.of(ReservationService.STOCK_PREFIX + sku,
                                ReservationService.RESERVED_PREFIX + sku),
                        String.valueOf(inv.getTotalQuantity()),
                        String.valueOf(inv.getReservedQuantity()));
                if (result != null && result == 1L) created++;
                else existing++;
            }
            log.info("Redis inventory bootstrap: created {} new keys, {} already present", created, existing);
        };
    }
}
