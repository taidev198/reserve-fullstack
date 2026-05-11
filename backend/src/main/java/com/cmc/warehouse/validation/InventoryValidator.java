package com.cmc.warehouse.validation;

import com.cmc.warehouse.common.AppMessages;
import com.cmc.warehouse.common.AppNumbers;

public final class InventoryValidator {

    private InventoryValidator() {}

    public static void validateInitialTotalQuantity(int totalQuantity) {
        if (totalQuantity < AppNumbers.ZERO) {
            throw new IllegalArgumentException(AppMessages.TOTAL_QUANTITY_MUST_BE_NON_NEGATIVE);
        }
    }

    public static void validatePositiveQuantity(int quantity) {
        if (quantity <= AppNumbers.ZERO) {
            throw new IllegalArgumentException(AppMessages.QUANTITY_MUST_BE_POSITIVE);
        }
    }
}
