package com.badminton.booking.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "inventory_batches",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_inventory_batches_batch_code",
                        columnNames = "batch_code"
                )
        }
)
public class InventoryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "batch_code",
            nullable = false,
            unique = true
    )
    private String batchCode;

    @ManyToOne
    @JoinColumn(
            name = "product_id",
            nullable = false
    )
    private Product product;

    @Column(
            name = "quantity_received_tubes",
            nullable = false
    )
    private Integer quantityReceivedTubes;

    @Column(
            name = "quantity_remaining_tubes",
            nullable = false
    )
    private Integer quantityRemainingTubes;

    @Column(
            name = "import_price_per_tube",
            nullable = false
    )
    private Long importPricePerTube;

    // Thời điểm hàng thực tế được nhập kho
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    // Thời điểm dữ liệu được tạo trong hệ thống
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public InventoryBatch() {
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (receivedAt == null) {
            receivedAt = now;
        }

        if (createdAt == null) {
            createdAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Integer getQuantityReceivedTubes() {
        return quantityReceivedTubes;
    }

    public void setQuantityReceivedTubes(
            Integer quantityReceivedTubes) {
        this.quantityReceivedTubes =
                quantityReceivedTubes;
    }

    public Integer getQuantityRemainingTubes() {
        return quantityRemainingTubes;
    }

    public void setQuantityRemainingTubes(
            Integer quantityRemainingTubes) {
        this.quantityRemainingTubes =
                quantityRemainingTubes;
    }

    public Long getImportPricePerTube() {
        return importPricePerTube;
    }

    public void setImportPricePerTube(
            Long importPricePerTube) {
        this.importPricePerTube = importPricePerTube;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(
            LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}