package com.badminton.booking.repository;

import com.badminton.booking.entity.InventoryIssueDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryIssueDetailRepository
        extends JpaRepository<InventoryIssueDetail, Long> {

    List<InventoryIssueDetail> findByIssueIdOrderByIdAsc(
            Long issueId
    );
}