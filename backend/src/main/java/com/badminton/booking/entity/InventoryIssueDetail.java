package com.badminton.booking.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory_issue_details")
public class InventoryIssueDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "issue_id", nullable = false)
    private InventoryIssue issue;

    @ManyToOne
    @JoinColumn(name = "batch_id", nullable = false)
    private InventoryBatch batch;

    @Column(name = "quantity_tubes", nullable = false)
    private Integer quantityTubes;

    public InventoryIssueDetail() {
    }

    public Long getId() {
        return id;
    }

    public InventoryIssue getIssue() {
        return issue;
    }

    public void setIssue(InventoryIssue issue) {
        this.issue = issue;
    }

    public InventoryBatch getBatch() {
        return batch;
    }

    public void setBatch(InventoryBatch batch) {
        this.batch = batch;
    }

    public Integer getQuantityTubes() {
        return quantityTubes;
    }

    public void setQuantityTubes(Integer quantityTubes) {
        this.quantityTubes = quantityTubes;
    }
}