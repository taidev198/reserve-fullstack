package com.taidev.warehouse.exception;

import com.taidev.warehouse.common.AppMessages;

public class DuplicateActiveReservationException extends RuntimeException {

    private final String orderId;

    public DuplicateActiveReservationException(String orderId, String reason) {
        super(AppMessages.DUPLICATE_ACTIVE_RESERVATION_TEMPLATE.formatted(orderId, reason));
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
