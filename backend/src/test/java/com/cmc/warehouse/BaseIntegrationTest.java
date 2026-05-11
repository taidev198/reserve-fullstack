package com.cmc.warehouse;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared scaffolding for integration tests. Brings up:
 *   - H2 in Postgres-compat mode (the durable store).
 *   - A real Redis 7 container via Testcontainers (the hot path).
 *
 * Both are reset to a known state before every test so order is
 * irrelevant. A single Redis container is shared across the whole
 * JVM — the static initialiser starts it once, Spring context is
 * cached, and {@link #resetWarehouseState()} reseeds both stores
 * before each test.
 *
 * Requires Docker. Without Docker the Testcontainers start fails
 * fast with a clear message.
 */
@SpringBootTest
public abstract class BaseIntegrationTest {

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected StringRedisTemplate redis;

    @BeforeEach
    void resetWarehouseState() {
        jdbc.update("DELETE FROM reservation_items");
        jdbc.update("DELETE FROM reservations");
        resetSku("A100", 100);
        resetSku("B200", 50);
        resetSku("C300", 20);
        resetSku("D400", 10);
        resetSku("E500", 5);

        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        reseedRedis("A100", 100);
        reseedRedis("B200", 50);
        reseedRedis("C300", 20);
        reseedRedis("D400", 10);
        reseedRedis("E500", 5);
    }

    private void resetSku(String sku, int totalQuantity) {
        jdbc.update("""
                UPDATE inventory
                   SET total_quantity = ?,
                       reserved_quantity = 0,
                       version = version + 1
                 WHERE product_id = (SELECT id FROM products WHERE sku = ?)
                """, totalQuantity, sku);
    }

    private void reseedRedis(String sku, int total) {
        redis.opsForValue().set("stock:" + sku, String.valueOf(total));
        redis.opsForValue().set("reserved:" + sku, "0");
    }
}
