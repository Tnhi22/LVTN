package com.badminton.booking.repository;

import com.badminton.booking.entity.InventoryIssue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryIssueRepository
        extends JpaRepository<InventoryIssue, Long> {
}