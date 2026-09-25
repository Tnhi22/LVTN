package com.badminton.booking.dto;

public class LoosePieceSaleRequest {

    private Long productId;
    private Integer quantityPieces;

    public LoosePieceSaleRequest() {
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantityPieces() {
        return quantityPieces;
    }

    public void setQuantityPieces(Integer quantityPieces) {
        this.quantityPieces = quantityPieces;
    }
}