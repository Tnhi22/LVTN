package com.badminton.booking.dto;

public class CounterSaleRequest {

    private Long productId;
    private Integer quantityTubes;

    public CounterSaleRequest() {
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

    public void setQuantityTubes(Integer quantityTubes) {
        this.quantityTubes = quantityTubes;
    }
}