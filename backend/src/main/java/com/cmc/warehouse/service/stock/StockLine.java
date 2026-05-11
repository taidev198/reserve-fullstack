package com.cmc.warehouse.service.stock;

import com.cmc.warehouse.validation.StockLineValidator;

/**
 * A single SKU+quantity pair, used to communicate intent between the
 * service layer and {@link StockReservationStrategy} implementations.
 * Keeping this independent of JPA entities means the Redis strategy
 * doesn't need to load entities just to talk about stock.
 */
public record StockLine(String sku, int quantity) {

    public StockLine {
        StockLineValidator.validate(sku, quantity);
    }
}
