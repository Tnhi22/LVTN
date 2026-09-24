package com.badminton.booking.controller;

import com.badminton.booking.dto.CounterSaleRequest;
import com.badminton.booking.dto.InventoryBatchRequest;
import com.badminton.booking.entity.InventoryBatch;
import com.badminton.booking.entity.InventoryIssue;
import com.badminton.booking.repository.InventoryBatchRepository;
import com.badminton.booking.service.InventoryService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory-batches")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryBatchRepository
            inventoryBatchRepository;

    public InventoryController(
            InventoryService inventoryService,
            InventoryBatchRepository
                    inventoryBatchRepository) {

        this.inventoryService = inventoryService;
        this.inventoryBatchRepository =
                inventoryBatchRepository;
    }

    // ADMIN nhập một lô hàng mới
    @PostMapping
    public InventoryBatch importBatch(
            @RequestBody InventoryBatchRequest request) {

        return inventoryService.importBatch(request);
    }

    // STAFF hoặc ADMIN bán sản phẩm tại quầy
    // Hệ thống tự trừ lô cũ trước theo FIFO
    @PostMapping("/counter-sales")
    public InventoryIssue sellAtCounter(
            @RequestBody CounterSaleRequest request) {

        return inventoryService.sellAtCounter(request);
    }

    // Xem các lô của một sản phẩm theo thứ tự FIFO
    @GetMapping("/product/{productId}")
    public List<InventoryBatch> getProductBatches(
            @PathVariable Long productId) {

        return inventoryBatchRepository
                .findByProductIdOrderByReceivedAtAscIdAsc(
                        productId
                );
    }
}