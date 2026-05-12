package com.taidev.warehouse.service.command;

import com.taidev.warehouse.common.AppMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ReservationLine(
        @NotBlank(message = AppMessages.SKU_MUST_NOT_BE_BLANK)
        String sku,
        @Positive(message = AppMessages.QUANTITY_MUST_BE_POSITIVE)
        int quantity
) {}
