package com.badminton.booking.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "products",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_products_name",
                        columnNames = "name"
                )
        }
)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String brand;

    // Số quả trong một ống
    @Column(name = "pieces_per_tube", nullable = false)
    private Integer piecesPerTube = 12;

    // Giá bán một ống
    @Column(name = "tube_price", nullable = false)
    private Long tubePrice;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Đường dẫn ảnh sản phẩm
    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    // Tổng số ống thực tế đang nằm trong kho
    @Column(
            name = "stock_quantity_tubes",
            nullable = false
    )
    private Integer stockQuantityTubes = 0;

    /*
     * Số ống đang được giữ cho các booking
     * chưa check-in.
     */
    @Column(
            name = "reserved_quantity_tubes",
            nullable = false
    )
    private Integer reservedQuantityTubes = 0;

    // Mức tồn kho bắt đầu cảnh báo
    @Column(
            name = "minimum_stock_tubes",
            nullable = false
    )
    private Integer minimumStockTubes = 20;

    // Mức tồn kho mục tiêu sau khi nhập
    @Column(
            name = "target_stock_tubes",
            nullable = false
    )
    private Integer targetStockTubes = 50;

    public Product() {
    }

    @PrePersist
    protected void onCreate() {

        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;

        if (piecesPerTube == null) {
            piecesPerTube = 12;
        }

        if (stockQuantityTubes == null) {
            stockQuantityTubes = 0;
        }

        if (reservedQuantityTubes == null) {
            reservedQuantityTubes = 0;
        }

        if (minimumStockTubes == null) {
            minimumStockTubes = 20;
        }

        if (targetStockTubes == null) {
            targetStockTubes = 50;
        }

        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
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

    public void setPiecesPerTube(
            Integer piecesPerTube) {

        this.piecesPerTube = piecesPerTube;
    }

    public Long getTubePrice() {
        return tubePrice;
    }

    public void setTubePrice(Long tubePrice) {
        this.tubePrice = tubePrice;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getStockQuantityTubes() {
        return stockQuantityTubes;
    }

    public void setStockQuantityTubes(
            Integer stockQuantityTubes) {

        this.stockQuantityTubes =
                stockQuantityTubes;
    }

    public Integer getReservedQuantityTubes() {
        return reservedQuantityTubes;
    }

    public void setReservedQuantityTubes(
            Integer reservedQuantityTubes) {

        this.reservedQuantityTubes =
                reservedQuantityTubes;
    }

    /*
     * Số lượng thực tế khách mới còn có thể đặt:
     * tồn vật lý - số đang giữ.
     *
     * @Transient: không tạo thêm cột database.
     */
    @Transient
    public Integer getAvailableQuantityTubes() {

        int stock = stockQuantityTubes == null
                ? 0
                : stockQuantityTubes;

        int reserved = reservedQuantityTubes == null
                ? 0
                : reservedQuantityTubes;

        return Math.max(stock - reserved, 0);
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
}