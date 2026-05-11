package com.cmc.warehouse.validation;

import com.cmc.warehouse.common.AppMessages;
import com.cmc.warehouse.common.AppNumbers;

public final class StockLineValidator {

    private StockLineValidator() {}

    public static void validate(String sku, int quantity) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException(AppMessages.SKU_MUST_NOT_BE_BLANK);
        }
        if (quantity <= AppNumbers.ZERO) {
            throw new IllegalArgumentException(AppMessages.QUANTITY_MUST_BE_POSITIVE);
        }
    }
}
