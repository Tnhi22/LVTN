package com.badminton.booking.repository;

import com.badminton.booking.entity.InventoryBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

import java.util.List;

public interface InventoryBatchRepository
        extends JpaRepository<InventoryBatch, Long> {

    List<InventoryBatch>
    findByProductIdOrderByReceivedAtAscIdAsc(
            Long productId
    );

    // Sau này dùng để bán theo FIFO
    List<InventoryBatch>
    findByProductIdAndQuantityRemainingTubesGreaterThanOrderByReceivedAtAscIdAsc(
            Long productId,
            Integer quantity
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select b from InventoryBatch b where b.id = :id")
        Optional<InventoryBatch> findForUpdate(@Param("id") Long id);
}