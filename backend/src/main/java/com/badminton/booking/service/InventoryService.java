package com.badminton.booking.service;

import com.badminton.booking.dto.CounterSaleRequest;
import com.badminton.booking.dto.InventoryBatchRequest;
import com.badminton.booking.entity.InventoryBatch;
import com.badminton.booking.entity.InventoryIssue;
import com.badminton.booking.entity.InventoryIssueDetail;
import com.badminton.booking.entity.Product;
import com.badminton.booking.exception.BusinessException;
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
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final InventoryIssueRepository inventoryIssueRepository;
    private final InventoryIssueDetailRepository
            inventoryIssueDetailRepository;

    public InventoryService(
            ProductRepository productRepository,
            InventoryBatchRepository inventoryBatchRepository,
            InventoryIssueRepository inventoryIssueRepository,
            InventoryIssueDetailRepository
                    inventoryIssueDetailRepository) {

        this.productRepository = productRepository;
        this.inventoryBatchRepository =
                inventoryBatchRepository;
        this.inventoryIssueRepository =
                inventoryIssueRepository;
        this.inventoryIssueDetailRepository =
                inventoryIssueDetailRepository;
    }

    // =====================================================
    // NHẬP MỘT LÔ HÀNG MỚI
    // =====================================================
    @Transactional
    public InventoryBatch importBatch(
            InventoryBatchRequest request) {

        if (request.getProductId() == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn sản phẩm"
            );
        }

        if (request.getQuantityTubes() == null
                || request.getQuantityTubes() <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Số lượng nhập phải lớn hơn 0"
            );
        }

        if (request.getImportPricePerTube() == null
                || request.getImportPricePerTube() <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Giá nhập một ống phải lớn hơn 0"
            );
        }

        Product product = productRepository
                .findById(request.getProductId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm"
                ));

        LocalDateTime receivedAt =
                request.getReceivedAt();

        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }

        if (receivedAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày giờ nhập không được ở tương lai"
            );
        }

        InventoryBatch batch = new InventoryBatch();

        batch.setBatchCode(
                generateBatchCode(
                        product.getId(),
                        receivedAt
                )
        );

        batch.setProduct(product);
        batch.setQuantityReceivedTubes(
                request.getQuantityTubes()
        );
        batch.setQuantityRemainingTubes(
                request.getQuantityTubes()
        );
        batch.setImportPricePerTube(
                request.getImportPricePerTube()
        );
        batch.setReceivedAt(receivedAt);

        Integer currentStock =
                product.getStockQuantityTubes();

        if (currentStock == null) {
            currentStock = 0;
        }

        product.setStockQuantityTubes(
                currentStock
                        + request.getQuantityTubes()
        );

        productRepository.save(product);

        return inventoryBatchRepository.save(batch);
    }

    // =====================================================
    // BÁN TẠI QUẦY VÀ TRỪ KHO THEO FIFO
    // =====================================================
    @Transactional
    public InventoryIssue sellAtCounter(
            CounterSaleRequest request) {

        if (request.getProductId() == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn sản phẩm"
            );
        }

        if (request.getQuantityTubes() == null
                || request.getQuantityTubes() <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Số lượng bán phải lớn hơn 0"
            );
        }

        Product product = productRepository
                .findById(request.getProductId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm"
                ));

        Integer currentStock =
                product.getStockQuantityTubes();

        if (currentStock == null) {
            currentStock = 0;
        }

        Integer reservedStock =
                product.getReservedQuantityTubes();

        if (reservedStock == null) {
            reservedStock = 0;
        }

        int availableStock =
                currentStock - reservedStock;

        if (availableStock
                < request.getQuantityTubes()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Không đủ hàng có thể bán. "
                            + "Tồn kho thực tế: "
                            + currentStock
                            + " ống, đang giữ cho booking: "
                            + reservedStock
                            + " ống, có thể bán: "
                            + Math.max(availableStock, 0)
                            + " ống"
            );
        }
        /*
         * Lấy các lô còn hàng, sắp xếp:
         * receivedAt tăng dần, id tăng dần.
         * Đây chính là thứ tự FIFO.
         */
        List<InventoryBatch> batches =
                inventoryBatchRepository
                        .findByProductIdAndQuantityRemainingTubesGreaterThanOrderByReceivedAtAscIdAsc(
                                product.getId(),
                                0
                        );

        int quantityToDeduct =
                request.getQuantityTubes();

        /*
         * Kiểm tra tổng số lượng trong các lô.
         * Việc này giúp phát hiện trường hợp tổng tồn kho
         * của Product bị lệch với tồn kho theo lô.
         */
        int totalBatchStock = batches.stream()
                .map(InventoryBatch
                        ::getQuantityRemainingTubes)
                .filter(quantity -> quantity != null)
                .mapToInt(Integer::intValue)
                .sum();

        if (totalBatchStock < quantityToDeduct) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Tồn kho sản phẩm không khớp với tồn kho theo lô"
            );
        }

        Long unitPrice = product.getTubePrice();

        if (unitPrice == null || unitPrice <= 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Sản phẩm chưa có giá bán hợp lệ"
            );
        }

        InventoryIssue issue =
                new InventoryIssue();

        issue.setIssueCode(generateIssueCode());
        issue.setProduct(product);
        issue.setIssueType("COUNTER_SALE");
        issue.setReferenceId(null);
        issue.setQuantityTubes(
                request.getQuantityTubes()
        );
        issue.setUnitPrice(unitPrice);
        issue.setTotalAmount(
                unitPrice
                        * request.getQuantityTubes()
        );
        issue.setIssuedAt(LocalDateTime.now());

        /*
         * Phải lưu phiếu xuất trước để có issue.id,
         * sau đó mới tạo các dòng chi tiết.
         */
        InventoryIssue savedIssue =
                inventoryIssueRepository.save(issue);

        int remainingToDeduct = quantityToDeduct;

        for (InventoryBatch batch : batches) {

            if (remainingToDeduct <= 0) {
                break;
            }

            Integer batchRemaining =
                    batch.getQuantityRemainingTubes();

            if (batchRemaining == null
                    || batchRemaining <= 0) {
                continue;
            }

            int quantityTaken = Math.min(
                    batchRemaining,
                    remainingToDeduct
            );

            // Trừ số lượng khỏi lô cũ trước
            batch.setQuantityRemainingTubes(
                    batchRemaining - quantityTaken
            );

            inventoryBatchRepository.save(batch);

            // Ghi lại phiếu đã lấy bao nhiêu từ lô này
            InventoryIssueDetail detail =
                    new InventoryIssueDetail();

            detail.setIssue(savedIssue);
            detail.setBatch(batch);
            detail.setQuantityTubes(quantityTaken);

            inventoryIssueDetailRepository.save(detail);

            remainingToDeduct -= quantityTaken;
        }

        if (remainingToDeduct > 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Không thể trừ đủ số lượng theo FIFO"
            );
        }

        // Cập nhật tổng tồn kho của sản phẩm
        product.setStockQuantityTubes(
                currentStock - quantityToDeduct
        );

        productRepository.save(product);

        return savedIssue;
    }

    // =====================================================
    // TẠO MÃ LÔ NHẬP
    // =====================================================
    private String generateBatchCode(
            Long productId,
            LocalDateTime receivedAt) {

        String datePart = receivedAt.format(
                DateTimeFormatter.ofPattern(
                        "yyyyMMddHHmmss"
                )
        );

        String randomPart = UUID.randomUUID()
                .toString()
                .substring(0, 6)
                .toUpperCase();

        return "P"
                + productId
                + "-"
                + datePart
                + "-"
                + randomPart;
    }

    // =====================================================
    // TẠO MÃ PHIẾU XUẤT KHO
    // =====================================================
    private String generateIssueCode() {

        String datePart = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern(
                        "yyyyMMddHHmmss"
                )
        );

        String randomPart = UUID.randomUUID()
                .toString()
                .substring(0, 6)
                .toUpperCase();

        return "XK-"
                + datePart
                + "-"
                + randomPart;
    }
}