package com.cmc.warehouse.service.stock;

import com.cmc.warehouse.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Low-level test for the Lua scripts: drives them directly via the
 * {@link org.springframework.data.redis.core.StringRedisTemplate}
 * exposed by {@link BaseIntegrationTest}. Verifies the script-level
 * semantics in isolation from the service:
 *
 *   - reserve.lua atomically decrements available stock.
 *   - reserve.lua rejects oversell with status code -1, no writes.
 *   - reserve.lua is all-or-nothing across SKUs.
 *   - release.lua restores reserved units.
 *   - consume.lua decrements both stock and reserved.
 */
class LuaStockScriptTest extends BaseIntegrationTest {

    @Autowired RedisScript<Long> reserveScript;
    @Autowired RedisScript<Long> releaseScript;
    @Autowired RedisScript<Long> consumeScript;

    @Test
    void reserve_decrements_available_atomically() {
        Long result = runScript(reserveScript, List.of("A100"), List.of(30));

        assertThat(result).isEqualTo(1L);
        assertThat(reservedFor("A100")).isEqualTo(30);
        assertThat(stockFor("A100")).isEqualTo(100);
    }

    @Test
    void reserve_rejects_oversell_with_negative_one() {
        runScript(reserveScript, List.of("A100"), List.of(90));

        Long result = runScript(reserveScript, List.of("A100"), List.of(11));

        assertThat(result).isEqualTo(-1L);
        assertThat(reservedFor("A100")).isEqualTo(90);
    }

    @Test
    void reserve_is_all_or_nothing_across_skus() {
        // B200 only has 50; asking for 60 must reject the whole call
        // and leave A100 untouched.
        Long result = runScript(reserveScript,
                List.of("A100", "B200"),
                List.of(10, 60));

        assertThat(result).isEqualTo(-1L);
        assertThat(reservedFor("A100")).isZero();
        assertThat(reservedFor("B200")).isZero();
    }

    @Test
    void release_returns_units_to_pool() {
        runScript(reserveScript, List.of("A100"), List.of(25));
        runScript(releaseScript, List.of("A100"), List.of(25));

        assertThat(reservedFor("A100")).isZero();
    }

    @Test
    void consume_reduces_both_stock_and_reserved() {
        runScript(reserveScript, List.of("A100"), List.of(25));
        runScript(consumeScript, List.of("A100"), List.of(25));

        assertThat(stockFor("A100")).isEqualTo(75);
        assertThat(reservedFor("A100")).isZero();
    }

    // ─── helpers ──────────────────────────────────────────────────────

    private Long runScript(RedisScript<Long> script, List<String> skus, List<Integer> quantities) {
        List<String> keys = new java.util.ArrayList<>(skus.size() * 2);
        for (String sku : skus) {
            keys.add("stock:" + sku);
            keys.add("reserved:" + sku);
        }
        Object[] args = quantities.stream().map(String::valueOf).toArray();
        return redis.execute(script, keys, args);
    }

    private int stockFor(String sku) {
        String value = redis.opsForValue().get("stock:" + sku);
        return value == null ? 0 : Integer.parseInt(value);
    }

    private int reservedFor(String sku) {
        String value = redis.opsForValue().get("reserved:" + sku);
        return value == null ? 0 : Integer.parseInt(value);
    }
}
