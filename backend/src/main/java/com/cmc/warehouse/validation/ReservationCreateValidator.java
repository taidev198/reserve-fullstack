package com.cmc.warehouse.validation;

import com.cmc.warehouse.common.AppMessages;
import com.cmc.warehouse.service.command.ReservationLine;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ReservationCreateValidator {

    private record CreateReservationInput(
            @NotBlank(message = AppMessages.ORDER_ID_MUST_NOT_BE_BLANK)
            String orderId,
            @NotEmpty(message = AppMessages.RESERVATION_MUST_HAVE_AT_LEAST_ONE_ITEM)
            @Valid List<ReservationLine> lines
    ) {}

    private final Validator validator;

    public ReservationCreateValidator(Validator validator) {
        this.validator = validator;
    }

    public void validate(String orderId, List<ReservationLine> lines) {
        Set<ConstraintViolation<CreateReservationInput>> violations =
                validator.validate(new CreateReservationInput(orderId, lines));
        if (violations.isEmpty()) {
            return;
        }
        String message = violations.stream()
                .map(ConstraintViolation::getMessage)
                .distinct()
                .collect(Collectors.joining("; "));
        throw new IllegalArgumentException(message);
    }
}
