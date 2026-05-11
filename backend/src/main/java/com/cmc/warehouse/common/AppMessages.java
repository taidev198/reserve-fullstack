package com.cmc.warehouse.common;

public final class AppMessages {

    private AppMessages() {}

    public static final String ORDER_ID_MUST_NOT_BE_BLANK = "orderId must not be blank";
    public static final String RESERVATION_MUST_HAVE_AT_LEAST_ONE_ITEM = "reservation must have at least one item";
    public static final String SKU_MUST_NOT_BE_BLANK = "sku must not be blank";
    public static final String QUANTITY_MUST_BE_POSITIVE = "quantity must be > 0";
    public static final String TOTAL_QUANTITY_MUST_BE_NON_NEGATIVE = "totalQuantity must be >= 0";
    public static final String CANNOT_RELEASE_MORE_THAN_RESERVED = "Cannot release more than is reserved";
    public static final String CANNOT_CONSUME_MORE_THAN_RESERVED = "Cannot consume more than is reserved";

    public static final String INSUFFICIENT_STOCK_TEMPLATE =
            "Insufficient stock for SKU %s: requested %d, available %d";
    public static final String DUPLICATE_ACTIVE_RESERVATION_TEMPLATE =
            "Active reservation already exists for orderId %s: %s";
    public static final String DUPLICATE_ACTIVE_RESERVATION_REASON_TEMPLATE =
            "existing reservation %d has different item lines";
}
