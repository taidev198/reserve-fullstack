package com.cmc.warehouse.api.controller;

import com.cmc.warehouse.api.dto.PageResponse;
import com.cmc.warehouse.api.dto.ReservationResponses.InventoryView;
import com.cmc.warehouse.api.mapper.InventoryMapper;
import com.cmc.warehouse.service.InventoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryMapper inventoryMapper;

    public InventoryController(InventoryService inventoryService, InventoryMapper inventoryMapper) {
        this.inventoryService = inventoryService;
        this.inventoryMapper = inventoryMapper;
    }

    @GetMapping
    public PageResponse<InventoryView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        var stockPage = inventoryService.listPage(PageRequest.of(page, size));
        return PageResponse.from(stockPage, inventoryMapper::toView);
    }
}
