package com.badminton.booking.service;

import com.badminton.booking.entity.InventoryBatch;
import com.badminton.booking.entity.InventoryIssue;
import com.badminton.booking.entity.InventoryIssueDetail;
import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.entity.Product;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.InventoryBatchRepository;
import com.badminton.booking.repository.InventoryIssueDetailRepository;
import com.badminton.booking.repository.InventoryIssueRepository;
import com.badminton.booking.repository.NormalBookingRepository;
import com.badminton.booking.repository.ProductRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class BookingInventoryService {

    private final ProductRepository productRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final InventoryIssueRepository inventoryIssueRepository;
    private final InventoryIssueDetailRepository
            inventoryIssueDetailRepository;
    private final NormalBookingRepository bookingRepository;

    public BookingInventoryService(
            ProductRepository productRepository,
            InventoryBatchRepository inventoryBatchRepository,
            InventoryIssueRepository inventoryIssueRepository,
            InventoryIssueDetailRepository
                    inventoryIssueDetailRepository,
            NormalBookingRepository bookingRepository) {

        this.productRepository = productRepository;
        this.inventoryBatchRepository =
                inventoryBatchRepository;
        this.inventoryIssueRepository =
                inventoryIssueRepository;
        this.inventoryIssueDetailRepository =
                inventoryIssueDetailRepository;
        this.bookingRepository = bookingRepository;
    }

    // =====================================================
    // GIỮ HÀNG KHI KHÁCH TẠO BOOKING
    // =====================================================
    @Transactional
    public Product reserveProduct(
            Long productId,
            Integer quantityTubes) {

        if (productId == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn sản phẩm cầu"
            );
        }

        if (quantityTubes == null || quantityTubes <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Số ống cầu phải lớn hơn 0"
            );
        }

        Product product = productRepository
                .findById(productId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm cầu"
                ));

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Sản phẩm cầu hiện không còn được bán"
            );
        }

        int availableQuantity =
                product.getAvailableQuantityTubes();

        if (availableQuantity < quantityTubes) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Không đủ ống cầu có thể đặt. Hiện còn "
                            + availableQuantity
                            + " ống"
            );
        }

        Integer currentReserved =
                product.getReservedQuantityTubes();

        if (currentReserved == null) {
            currentReserved = 0;
        }

        product.setReservedQuantityTubes(
                currentReserved + quantityTubes
        );

        return productRepository.save(product);
    }

    // =====================================================
    // GIẢI PHÓNG HÀNG KHI BOOKING BỊ HỦY HOẶC NO_SHOW
    // =====================================================
    @Transactional
    public void releaseReservation(
            NormalBooking booking) {

        if (booking == null
                || !Boolean.TRUE.equals(
                        booking.getShuttlecockReservationActive()
                )) {
            return;
        }

        Product product =
                booking.getShuttlecockProduct();

        Integer quantity =
                booking.getShuttlecockQuantityTubes();

        if (product == null
                || quantity == null
                || quantity <= 0) {

            booking.setShuttlecockReservationActive(false);
            bookingRepository.save(booking);
            return;
        }

        Integer currentReserved =
                product.getReservedQuantityTubes();

        if (currentReserved == null) {
            currentReserved = 0;
        }

        product.setReservedQuantityTubes(
                Math.max(currentReserved - quantity, 0)
        );

        booking.setShuttlecockReservationActive(false);

        productRepository.save(product);
        bookingRepository.save(booking);
    }

    // =====================================================
    // CHECK-IN: XUẤT KHO THEO FIFO
    // =====================================================
    @Transactional
    public InventoryIssue issueForBooking(
            NormalBooking booking) {

        if (booking == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Booking không hợp lệ"
            );
        }

        Integer quantity =
                booking.getShuttlecockQuantityTubes();

        /*
         * Booking không đặt cầu thì không cần xuất kho.
         */
        if (quantity == null || quantity <= 0) {
            return null;
        }

        /*
         * Ngăn check-in hoặc gọi xử lý nhiều lần
         * làm trừ kho nhiều lần.
         */
        if (Boolean.TRUE.equals(
                booking.getShuttlecockIssued())) {

            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Ống cầu của booking này đã được xuất kho"
            );
        }

        if (!Boolean.TRUE.equals(
                booking.getShuttlecockReservationActive())) {

            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Booking không còn giữ ống cầu"
            );
        }

        Product product =
                booking.getShuttlecockProduct();

        if (product == null) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Booking không có sản phẩm cầu hợp lệ"
            );
        }

        Integer physicalStock =
                product.getStockQuantityTubes();

        if (physicalStock == null) {
            physicalStock = 0;
        }

        if (physicalStock < quantity) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Kho không đủ ống cầu để xuất"
            );
        }

        List<InventoryBatch> batches =
                inventoryBatchRepository
                        .findByProductIdAndQuantityRemainingTubesGreaterThanOrderByReceivedAtAscIdAsc(
                                product.getId(),
                                0
                        );

        int totalBatchStock = batches.stream()
                .map(InventoryBatch
                        ::getQuantityRemainingTubes)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .sum();

        if (totalBatchStock < quantity) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Tồn kho sản phẩm không khớp với tồn kho theo lô"
            );
        }

        Long unitPrice =
                booking.getShuttlecockUnitPrice();

        if (unitPrice == null || unitPrice <= 0) {
            unitPrice = product.getTubePrice();
        }

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
        issue.setIssueType("NORMAL_BOOKING");
        issue.setReferenceId(booking.getId());
        issue.setQuantityTubes(quantity);
        issue.setUnitPrice(unitPrice);
        issue.setTotalAmount(unitPrice * quantity);
        issue.setIssuedAt(LocalDateTime.now());

        InventoryIssue savedIssue =
                inventoryIssueRepository.save(issue);

        int remainingToDeduct = quantity;

        /*
         * Danh sách đã được sắp theo:
         * receivedAt ASC, id ASC.
         * Vì vậy lô cũ luôn bị trừ trước.
         */
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

            batch.setQuantityRemainingTubes(
                    batchRemaining - quantityTaken
            );

            inventoryBatchRepository.save(batch);

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
                    "Không thể xuất đủ ống cầu theo FIFO"
            );
        }

        Integer currentReserved =
                product.getReservedQuantityTubes();

        if (currentReserved == null) {
            currentReserved = 0;
        }

        product.setStockQuantityTubes(
                physicalStock - quantity
        );

        product.setReservedQuantityTubes(
                Math.max(currentReserved - quantity, 0)
        );

        booking.setShuttlecockReservationActive(false);
        booking.setShuttlecockIssued(true);

        productRepository.save(product);
        bookingRepository.save(booking);

        return savedIssue;
    }

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

        return "XK-BK-"
                + datePart
                + "-"
                + randomPart;
    }
}