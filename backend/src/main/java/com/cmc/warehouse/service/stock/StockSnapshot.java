package com.cmc.warehouse.service.stock;

/**
 * What the dashboard sees for a single SKU. Strategy implementations
 * produce this view from whichever store they consider authoritative
 * (Redis for the Redis strategy, Postgres for the JPA strategy).
 *
 * {@code version} is what enables CAS — clients can read a snapshot,
 * do some local logic, and then ask the strategy to perform a
 * mutation conditional on the version still being current.
 */
public record StockSnapshot(
        String sku,
        String name,
        int total,
        int reserved,
        long version
) {
    public int available() {
        return total - reserved;
    }
}
