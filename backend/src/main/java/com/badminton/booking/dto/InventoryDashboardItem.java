package com.badminton.booking.dto;

public class InventoryDashboardItem {

    private Long productId;
    private String productName;
    private String brand;
    private Integer stockQuantityTubes;
    private Integer reservedQuantityTubes;
    private Integer availableQuantityTubes;
    private Integer minimumStockTubes;
    private Integer targetStockTubes;
    private String stockStatus;
    private Integer suggestedImportTubes;

    public InventoryDashboardItem() {
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public Integer getStockQuantityTubes() {
        return stockQuantityTubes;
    }

    public void setStockQuantityTubes(
            Integer stockQuantityTubes) {
        this.stockQuantityTubes = stockQuantityTubes;
    }

    public Integer getReservedQuantityTubes() {
        return reservedQuantityTubes;
    }

    public void setReservedQuantityTubes(
            Integer reservedQuantityTubes) {
        this.reservedQuantityTubes =
                reservedQuantityTubes;
    }

    public Integer getAvailableQuantityTubes() {
        return availableQuantityTubes;
    }

    public void setAvailableQuantityTubes(
            Integer availableQuantityTubes) {
        this.availableQuantityTubes =
                availableQuantityTubes;
    }

    public Integer getMinimumStockTubes() {
        return minimumStockTubes;
    }

    public void setMinimumStockTubes(
            Integer minimumStockTubes) {
        this.minimumStockTubes =
                minimumStockTubes;
    }

    public Integer getTargetStockTubes() {
        return targetStockTubes;
    }

    public void setTargetStockTubes(
            Integer targetStockTubes) {
        this.targetStockTubes =
                targetStockTubes;
    }

    public String getStockStatus() {
        return stockStatus;
    }

    public void setStockStatus(String stockStatus) {
        this.stockStatus = stockStatus;
    }

    public Integer getSuggestedImportTubes() {
        return suggestedImportTubes;
    }

    public void setSuggestedImportTubes(
            Integer suggestedImportTubes) {
        this.suggestedImportTubes =
                suggestedImportTubes;
    }
}