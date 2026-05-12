package com.taidev.warehouse.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** All request bodies hitting POST /reservations. */
public final class ReservationRequests {

    private ReservationRequests() {}

    public record CreateReservationRequest(
            @NotBlank String orderId,
            @NotEmpty @Valid List<LineRequest> items
    ) {}

    public record LineRequest(
            @NotBlank String sku,
            @Min(1)   int quantity
    ) {}
}
