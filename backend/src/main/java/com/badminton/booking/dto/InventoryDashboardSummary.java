package com.badminton.booking.dto;

public class InventoryDashboardSummary {

    private Integer totalProducts;
    private Integer totalStockTubes;
    private Integer totalReservedTubes;
    private Integer totalAvailableTubes;
    private Integer lowStockProducts;
    private Integer outOfStockProducts;

    public InventoryDashboardSummary() {
    }

    public Integer getTotalProducts() {
        return totalProducts;
    }

    public void setTotalProducts(Integer totalProducts) {
        this.totalProducts = totalProducts;
    }

    public Integer getTotalStockTubes() {
        return totalStockTubes;
    }

    public void setTotalStockTubes(
            Integer totalStockTubes) {
        this.totalStockTubes = totalStockTubes;
    }

    public Integer getTotalReservedTubes() {
        return totalReservedTubes;
    }

    public void setTotalReservedTubes(
            Integer totalReservedTubes) {
        this.totalReservedTubes = totalReservedTubes;
    }

    public Integer getTotalAvailableTubes() {
        return totalAvailableTubes;
    }

    public void setTotalAvailableTubes(
            Integer totalAvailableTubes) {
        this.totalAvailableTubes = totalAvailableTubes;
    }

    public Integer getLowStockProducts() {
        return lowStockProducts;
    }

    public void setLowStockProducts(
            Integer lowStockProducts) {
        this.lowStockProducts = lowStockProducts;
    }

    public Integer getOutOfStockProducts() {
        return outOfStockProducts;
    }

    public void setOutOfStockProducts(
            Integer outOfStockProducts) {
        this.outOfStockProducts = outOfStockProducts;
    }
}