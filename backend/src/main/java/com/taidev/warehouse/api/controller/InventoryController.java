package com.taidev.warehouse.api.controller;

import com.taidev.warehouse.api.dto.PageResponse;
import com.taidev.warehouse.api.dto.ReservationResponses.InventoryView;
import com.taidev.warehouse.api.mapper.InventoryMapper;
import com.taidev.warehouse.service.InventoryService;
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
