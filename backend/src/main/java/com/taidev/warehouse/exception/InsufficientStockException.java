package com.taidev.warehouse.exception;

import com.taidev.warehouse.common.AppMessages;

public class InsufficientStockException extends RuntimeException {

    private final String sku;
    private final int requested;
    private final int available;

    public InsufficientStockException(String sku, int requested, int available) {
        super(AppMessages.INSUFFICIENT_STOCK_TEMPLATE
                .formatted(sku, requested, available));
        this.sku = sku;
        this.requested = requested;
        this.available = available;
    }

    public String getSku()       { return sku; }
    public int getRequested()    { return requested; }
    public int getAvailable()    { return available; }
}
