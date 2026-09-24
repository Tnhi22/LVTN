package com.badminton.booking.service;

import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.entity.InventoryBatch;
import com.badminton.booking.entity.InventoryIssue;
import com.badminton.booking.entity.InventoryIssueDetail;
import com.badminton.booking.entity.Product;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.DailyVisitorSessionRepository;
import com.badminton.booking.repository.InventoryBatchRepository;
import com.badminton.booking.repository.InventoryIssueDetailRepository;
import com.badminton.booking.repository.InventoryIssueRepository;
import com.badminton.booking.repository.ProductRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class DailyVisitorInventoryService {

    private final ProductRepository productRepository;
    private final InventoryBatchRepository
            inventoryBatchRepository;
    private final InventoryIssueRepository
            inventoryIssueRepository;
    private final InventoryIssueDetailRepository
            inventoryIssueDetailRepository;
    private final DailyVisitorSessionRepository
            sessionRepository;

    public DailyVisitorInventoryService(
            ProductRepository productRepository,
            InventoryBatchRepository inventoryBatchRepository,
            InventoryIssueRepository inventoryIssueRepository,
            InventoryIssueDetailRepository
                    inventoryIssueDetailRepository,
            DailyVisitorSessionRepository sessionRepository) {

        this.productRepository = productRepository;
        this.inventoryBatchRepository =
                inventoryBatchRepository;
        this.inventoryIssueRepository =
                inventoryIssueRepository;
        this.inventoryIssueDetailRepository =
                inventoryIssueDetailRepository;
        this.sessionRepository = sessionRepository;
    }

    /**
     * Xuất đúng 1 ống cầu cho Daily Visitor theo FIFO.
     * Hàm này an toàn khi gọi nhiều lần vì kiểm tra
     * shuttlecockIssued trước khi trừ kho.
     */
    @Transactional
    public void issueForSession(
            DailyVisitorSession session) {

        if (session == null || session.getId() == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Session Daily Visitor không hợp lệ"
            );
        }

        // Đã xuất rồi thì không xuất lần thứ hai
        if (Boolean.TRUE.equals(
                session.getShuttlecockIssued())) {
            return;
        }

        Product product =
                session.getShuttlecockProduct();

        if (product == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Buổi chơi chưa được chọn loại cầu"
            );
        }

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Loại cầu được chọn đang ngừng bán"
            );
        }

        int quantity = 1;

        int physicalStock =
                product.getStockQuantityTubes() == null
                        ? 0
                        : product.getStockQuantityTubes();

        int reservedStock =
                product.getReservedQuantityTubes() == null
                        ? 0
                        : product.getReservedQuantityTubes();

        int availableStock =
                physicalStock - reservedStock;

        // Không được lấy số cầu Normal Booking đang giữ
        if (availableStock < quantity) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Loại cầu được chọn không đủ tồn kho khả dụng. "
                            + "Vui lòng đổi sang loại cầu khác"
            );
        }

        List<InventoryBatch> batches =
                inventoryBatchRepository
                        .findByProductIdAndQuantityRemainingTubesGreaterThanOrderByReceivedAtAscIdAsc(
                                product.getId(),
                                0
                        );

        int remainingToIssue = quantity;

        InventoryIssue issue =
                new InventoryIssue();

        issue.setIssueCode(
                generateIssueCode(session.getId())
        );
        issue.setProduct(product);
        issue.setIssueType("DAILY_VISITOR");
        issue.setReferenceId(session.getId());
        issue.setQuantityTubes(quantity);

        /*
         * Daily Visitor không phải bán riêng ống cầu.
         * Tiền cầu đã nằm trong phí tham gia,
         * nên không ghi thêm doanh thu bán hàng.
         */
        issue.setUnitPrice(0L);
        issue.setTotalAmount(0L);
        issue.setIssuedAt(LocalDateTime.now());

        issue = inventoryIssueRepository.save(issue);

        for (InventoryBatch batch : batches) {

            if (remainingToIssue <= 0) {
                break;
            }

            int batchRemaining =
                    batch.getQuantityRemainingTubes() == null
                            ? 0
                            : batch.getQuantityRemainingTubes();

            if (batchRemaining <= 0) {
                continue;
            }

            int taken =
                    Math.min(
                            batchRemaining,
                            remainingToIssue
                    );

            batch.setQuantityRemainingTubes(
                    batchRemaining - taken
            );

            inventoryBatchRepository.save(batch);

            InventoryIssueDetail detail =
                    new InventoryIssueDetail();

            detail.setIssue(issue);
            detail.setBatch(batch);
            detail.setQuantityTubes(taken);

            inventoryIssueDetailRepository.save(detail);

            remainingToIssue -= taken;
        }

        if (remainingToIssue > 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Dữ liệu tồn kho theo lô không đủ. "
                            + "Vui lòng kiểm tra lại các lô hàng"
            );
        }

        product.setStockQuantityTubes(
                physicalStock - quantity
        );

        productRepository.save(product);

        session.setShuttlecockQuantityTubes(quantity);
        session.setShuttlecockIssued(true);
        session.setShuttlecockIssuedAt(
                LocalDateTime.now()
        );

        sessionRepository.save(session);
    }

    private String generateIssueCode(
            Long sessionId) {

        String timePart =
                LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern(
                                "yyyyMMddHHmmss"
                        )
                );

        String randomPart =
                UUID.randomUUID()
                        .toString()
                        .substring(0, 6)
                        .toUpperCase();

        return "DV-"
                + sessionId
                + "-"
                + timePart
                + "-"
                + randomPart;
    }
}