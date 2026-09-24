package com.badminton.booking.dto;

public class ProductRequest {

    private String name;
    private String brand;
    private Integer piecesPerTube;
    private Long piecePrice;
    private Long tubePrice;

    public ProductRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public Integer getPiecesPerTube() {
        return piecesPerTube;
    }

    public void setPiecesPerTube(Integer piecesPerTube) {
        this.piecesPerTube = piecesPerTube;
    }

    public Long getPiecePrice() {
        return piecePrice;
    }

    public void setPiecePrice(Long piecePrice) {
        this.piecePrice = piecePrice;
    }

    public Long getTubePrice() {
        return tubePrice;
    }

    public void setTubePrice(Long tubePrice) {
        this.tubePrice = tubePrice;
    }
}