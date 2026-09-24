package com.badminton.booking.repository;

import com.badminton.booking.entity.InventoryBatch;
import org.springframework.data.jpa.repository.JpaRepository;

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
}