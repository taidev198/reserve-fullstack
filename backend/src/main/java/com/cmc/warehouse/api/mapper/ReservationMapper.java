package com.cmc.warehouse.api.mapper;

import com.cmc.warehouse.api.dto.ReservationResponses.ItemView;
import com.cmc.warehouse.api.dto.ReservationResponses.ReservationView;
import com.cmc.warehouse.domain.reservation.Reservation;
import org.springframework.stereotype.Component;

@Component
public class ReservationMapper {

    public ReservationView toView(Reservation reservation) {
        return new ReservationView(
                reservation.getId(),
                reservation.getOrderId(),
                reservation.getStatus(),
                reservation.currentState().canConfirm(),
                reservation.currentState().canCancel(),
                reservation.getItems().stream()
                        .map(i -> new ItemView(i.getProduct().getSku(), i.getProduct().getName(), i.getQuantity()))
                        .toList(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }
}
