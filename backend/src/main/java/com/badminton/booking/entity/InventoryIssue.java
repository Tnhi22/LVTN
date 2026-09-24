package com.badminton.booking.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "inventory_issues",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_inventory_issues_issue_code",
                        columnNames = "issue_code"
                )
        }
)
public class InventoryIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "issue_code",
            nullable = false,
            unique = true
    )
    private String issueCode;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /*
     * COUNTER_SALE
     * NORMAL_BOOKING
     * DAILY_VISITOR
     */
    @Column(name = "issue_type", nullable = false)
    private String issueType;

    /*
     * ID booking hoặc session liên quan.
     * Bán tại quầy có thể để null.
     */
    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "quantity_tubes", nullable = false)
    private Integer quantityTubes;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    public InventoryIssue() {
    }

    @PrePersist
    protected void onCreate() {
        if (issuedAt == null) {
            issuedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getIssueCode() {
        return issueCode;
    }

    public void setIssueCode(String issueCode) {
        this.issueCode = issueCode;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }

    public Integer getQuantityTubes() {
        return quantityTubes;
    }

    public void setQuantityTubes(Integer quantityTubes) {
        this.quantityTubes = quantityTubes;
    }

    public Long getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Long unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }
}