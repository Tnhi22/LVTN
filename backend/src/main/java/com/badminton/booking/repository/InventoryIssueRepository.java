package com.badminton.booking.repository;

import com.badminton.booking.entity.InventoryIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryIssueRepository
        extends JpaRepository<InventoryIssue, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryIssue i where i.id = :id")
    Optional<InventoryIssue> findByIdForUpdate(@Param("id") Long id);

    List<InventoryIssue> findByIssueTypeInAndReferenceIdOrderByIssuedAtAscIdAsc(
            List<String> types, Long referenceId);
}