package com.badminton.booking.dto;

import java.time.LocalDateTime;

public class InventoryBatchRequest {

    private Long productId;
    private Integer quantityTubes;
    private Long importPricePerTube;
    private LocalDateTime receivedAt;

    public InventoryBatchRequest() {
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantityTubes() {
        return quantityTubes;
    }

    public void setQuantityTubes(
            Integer quantityTubes) {
        this.quantityTubes = quantityTubes;
    }

    public Long getImportPricePerTube() {
        return importPricePerTube;
    }

    public void setImportPricePerTube(
            Long importPricePerTube) {
        this.importPricePerTube =
                importPricePerTube;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(
            LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }
}