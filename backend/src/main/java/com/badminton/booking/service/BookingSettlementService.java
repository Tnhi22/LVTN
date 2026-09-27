package com.badminton.booking.service;

import com.badminton.booking.dto.BookingReceipt;
import com.badminton.booking.dto.SettlementRequest;
import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.NormalBookingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class BookingSettlementService {

    private final NormalBookingRepository bookings;

    public BookingSettlementService(NormalBookingRepository bookings) {
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public BookingReceipt preview(Long id) {
        NormalBooking booking = bookings.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy booking"
                ));

        if (!"CHECKED_IN".equals(booking.getStatus())
                && !"COMPLETED".equals(booking.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Booking chưa được check-in"
            );
        }

        return receipt(booking);
    }

    @Transactional
    public BookingReceipt settle(
            Long id,
            SettlementRequest request,
            Long staffId
    ) {
        NormalBooking booking = bookings.findByIdForSettlement(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy booking"
                ));

        if (!"CHECKED_IN".equals(booking.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Chỉ booking đã check-in và chưa hoàn thành mới được chốt tiền"
            );
        }

        long shuttle = booking.getShuttlecockAmount() == null
                ? 0
                : booking.getShuttlecockAmount();

        if (booking.getTotalAmount() == null
                || shuttle < 0
                || booking.getTotalAmount() < shuttle) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Booking chưa có tổng tiền hợp lệ"
            );
        }

        if (request == null
                || request.paymentMethod() == null
                || request.amountReceived() == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Cần nhập phương thức và số tiền đã nhận"
            );
        }

        String method = request.paymentMethod()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!method.equals("CASH")
                && !method.equals("BANK_TRANSFER")) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Phương thức thanh toán phải là CASH hoặc BANK_TRANSFER"
            );
        }

        long total = booking.getTotalAmount();
        long received = request.amountReceived();

        if (received < total
                || (method.equals("BANK_TRANSFER")
                    && received != total)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Số tiền nhận chưa hợp lệ so với tổng hóa đơn"
            );
        }

        booking.setPaymentMethod(method);
        booking.setAmountReceived(received);
        booking.setCompletedAt(LocalDateTime.now());
        booking.setCompletedBy(staffId);
        booking.setStatus("COMPLETED");

        return receipt(bookings.save(booking));
    }

    private BookingReceipt receipt(NormalBooking booking) {
        long shuttle = booking.getShuttlecockAmount() == null
                ? 0
                : booking.getShuttlecockAmount();

        Long total = booking.getTotalAmount();
        Long court = total == null ? null : total - shuttle;

        Long change = total == null
                || booking.getAmountReceived() == null
                ? null
                : booking.getAmountReceived() - total;

        return new BookingReceipt(
                booking.getId(),
                booking.getStatus(),
                court,
                shuttle,
                total,
                booking.getPaymentMethod(),
                booking.getAmountReceived(),
                change,
                booking.getCompletedAt(),
                booking.getCompletedBy()
        );
    }
}