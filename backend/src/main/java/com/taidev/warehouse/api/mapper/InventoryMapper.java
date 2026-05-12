package com.taidev.warehouse.api.mapper;

import com.taidev.warehouse.api.dto.ReservationResponses.InventoryView;
import com.taidev.warehouse.service.stock.StockSnapshot;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    public InventoryView toView(StockSnapshot snapshot) {
        return new InventoryView(
                snapshot.sku(),
                snapshot.name(),
                snapshot.total(),
                snapshot.reserved(),
                snapshot.available(),
                snapshot.version()
        );
    }
}
