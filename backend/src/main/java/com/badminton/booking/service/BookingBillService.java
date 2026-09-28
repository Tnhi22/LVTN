package com.badminton.booking.service;

import com.badminton.booking.dto.BookingAddItemRequest;
import com.badminton.booking.entity.InventoryIssueDetail;
import com.badminton.booking.entity.InventoryBatch;
import com.badminton.booking.entity.Product;
import com.badminton.booking.repository.InventoryIssueDetailRepository;
import com.badminton.booking.repository.ProductRepository;
import java.time.LocalDateTime;
import java.util.List;
import com.badminton.booking.dto.BookingReceipt;
import com.badminton.booking.dto.CounterSaleRequest;
import com.badminton.booking.dto.LoosePieceSaleRequest;
import com.badminton.booking.entity.InventoryIssue;
import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.InventoryIssueRepository;
import com.badminton.booking.repository.NormalBookingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingBillService {
    private final NormalBookingRepository bookings;
    private final InventoryService inventory;
    private final InventoryIssueRepository issues;
    private final BookingSettlementService settlements;
    private final InventoryIssueDetailRepository issueDetails;
    private final ProductRepository products;

    public BookingBillService(NormalBookingRepository bookings,
                              InventoryService inventory,
                              InventoryIssueRepository issues,
                              BookingSettlementService settlements,
                              InventoryIssueDetailRepository issueDetails,
                              ProductRepository products) {
        this.bookings = bookings;
        this.inventory = inventory;
        this.issues = issues;
        this.settlements = settlements;
        this.issueDetails = issueDetails;
        this.products = products;
    }

    @Transactional
    public BookingReceipt addItem(Long bookingId, BookingAddItemRequest request) {
        NormalBooking booking = bookings.findByIdForSettlement(bookingId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy booking"));
        if (!"CHECKED_IN".equals(booking.getStatus())
                && !"COMPLETED".equals(booking.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Chỉ được thêm hàng vào booking đã check-in hoặc hoàn thành");
        }
        if (booking.getPaidAt() != null) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Hóa đơn đã được thanh toán");
        }
        int tubes = request == null || request.quantityTubes() == null
                ? 0 : request.quantityTubes();
        int pieces = request == null || request.quantityPieces() == null
                ? 0 : request.quantityPieces();
        if (request == null || request.productId() == null
                || tubes < 0 || pieces < 0 || (tubes > 0 && pieces > 0)
                || (tubes == 0 && pieces == 0)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Nhập productId và một số lượng ống hoặc số quả lớn hơn 0");
        }

        InventoryIssue issue;
        if (tubes > 0) {
            CounterSaleRequest sale = new CounterSaleRequest();
            sale.setProductId(request.productId());
            sale.setQuantityTubes(tubes);
            issue = inventory.sellAtCounter(sale);
        } else {
            LoosePieceSaleRequest sale = new LoosePieceSaleRequest();
            sale.setProductId(request.productId());
            sale.setQuantityPieces(pieces);
            issue = inventory.sellLoosePieces(sale);
        }
        // Phiếu xuất gắn với booking; không còn là doanh thu bán lẻ độc lập.
        issue.setIssueType("NORMAL_BOOKING_ADDON");
        issue.setReferenceId(bookingId);
        issues.save(issue);

        long oldTotal = booking.getTotalAmount() == null ? 0 : booking.getTotalAmount();
        long oldShuttle = booking.getShuttlecockAmount() == null
                ? 0 : booking.getShuttlecockAmount();
        if (booking.getTotalAmount() == null || oldTotal < oldShuttle) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Booking chưa có tổng tiền hợp lệ");
        }
        try {
            booking.setTotalAmount(Math.addExact(oldTotal, issue.getTotalAmount()));
            booking.setShuttlecockAmount(Math.addExact(oldShuttle, issue.getTotalAmount()));
        } catch (ArithmeticException ex) {
            throw new BusinessException(HttpStatus.CONFLICT, "Tổng tiền vượt giới hạn");
        }
        bookings.save(booking);
        return settlements.preview(bookingId);
    }
    @Transactional
    public BookingReceipt cancelItem(Long bookingId, Long issueId, Long staffId) {
        NormalBooking booking = bookings.findByIdForSettlement(bookingId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy booking"));
        if (booking.getPaidAt() != null) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Hóa đơn đã được thanh toán");
        }
        if (!"CHECKED_IN".equals(booking.getStatus())
                && !"COMPLETED".equals(booking.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Không thể hủy hàng của booking này");
        }

        InventoryIssue issue = issues.findByIdForUpdate(issueId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy dòng mua"));
        if (!"NORMAL_BOOKING_ADDON".equals(issue.getIssueType())
                || !bookingId.equals(issue.getReferenceId())
                || issue.getCancelledAt() != null) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Dòng mua không thuộc booking này hoặc đã hủy");
        }

        Product product = products.findByIdForUpdate(issue.getProduct().getId())
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT,
                        "Không tìm thấy sản phẩm để hoàn kho"));
        List<InventoryIssueDetail> details = issueDetails.findByIssueIdOrderByIdAsc(issueId);
        if (details.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Phiếu xuất thiếu thông tin lô, không thể hoàn kho");
        }
        long returnedTubes = 0;
        long returnedPieces = 0;
        for (InventoryIssueDetail detail : details) {
            InventoryBatch batch = detail.getBatch();
            if (!"RECEIVED".equals(batch.getStatus())
                    || !product.getId().equals(batch.getProduct().getId())) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "Lô hàng không hợp lệ để hoàn kho");
            }
            int tubes = detail.getQuantityTubes() == null ? 0 : detail.getQuantityTubes();
            int pieces = detail.getQuantityPieces() == null ? 0 : detail.getQuantityPieces();
            if (tubes < 0 || pieces < 0) {
                throw new BusinessException(HttpStatus.CONFLICT, "Chi tiết xuất kho không hợp lệ");
            }
            batch.setQuantityRemainingTubes(Math.addExact(
                    batch.getQuantityRemainingTubes(), tubes));
            batch.setLoosePiecesRemaining(Math.addExact(
                    batch.getLoosePiecesRemaining() == null ? 0 : batch.getLoosePiecesRemaining(), pieces));
            returnedTubes += tubes;
            returnedPieces += pieces;
        }
        if (returnedTubes != (issue.getQuantityTubes() == null ? 0 : issue.getQuantityTubes())
                || returnedPieces != (issue.getQuantityPieces() == null ? 0 : issue.getQuantityPieces())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Số lượng xuất kho không khớp, không thể hoàn kho");
        }
        if (returnedTubes > 0) {
            product.setStockQuantityTubes(Math.addExact(
                    product.getStockQuantityTubes(), Math.toIntExact(returnedTubes)));
        }
        if (booking.getTotalAmount() == null || booking.getShuttlecockAmount() == null
                || booking.getTotalAmount() < issue.getTotalAmount()
                || booking.getShuttlecockAmount() < issue.getTotalAmount()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Tổng tiền booking không hợp lệ để trừ dòng mua");
        }
        booking.setTotalAmount(booking.getTotalAmount() - issue.getTotalAmount());
        booking.setShuttlecockAmount(booking.getShuttlecockAmount() - issue.getTotalAmount());
        issue.setIssueType("NORMAL_BOOKING_ADDON_CANCELLED");
        issue.setCancelledAt(LocalDateTime.now());
        issue.setCancelledBy(staffId);
        issues.save(issue);
        products.save(product);
        bookings.save(booking);
        return settlements.preview(bookingId);
    }

}
