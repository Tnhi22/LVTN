package com.badminton.booking.service;

import com.badminton.booking.dto.CourtSearchRequest;
import com.badminton.booking.dto.CourtSearchResponse;
import com.badminton.booking.entity.Court;
import com.badminton.booking.entity.CourtPrice;
import com.badminton.booking.entity.DailyVisitorSchedule;
import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.*;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Service
public class CourtSearchService {

    private final CourtRepository courts;
    private final CourtPriceRepository prices;
    private final NormalBookingRepository bookings;
    private final CourtMaintenanceRepository maintenance;
    private final DailyVisitorScheduleRepository schedules;
    private final DailyVisitorSessionRepository sessions;

    public CourtSearchService(
            CourtRepository courts,
            CourtPriceRepository prices,
            NormalBookingRepository bookings,
            CourtMaintenanceRepository maintenance,
            DailyVisitorScheduleRepository schedules,
            DailyVisitorSessionRepository sessions) {

        this.courts = courts;
        this.prices = prices;
        this.bookings = bookings;
        this.maintenance = maintenance;
        this.schedules = schedules;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public List<CourtSearchResponse> search(
            CourtSearchRequest request) {

        validate(request);

        LocalDateTime start = LocalDateTime.of(
                request.date(), request.startTime());

        LocalDateTime end = LocalDateTime.of(
                request.date(), request.endTime());

        long duration = Duration.between(start, end).toMinutes();

        List<Court> candidates;

        if (request.courtId() == null) {
            candidates = courts.findAll();
        } else {
            Court court = courts.findById(request.courtId())
                    .orElseThrow(() -> new BusinessException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy sân"
                    ));

            candidates = List.of(court);
        }

        List<DailyVisitorSession> daySessions =
                sessions.findBySessionDate(request.date());

        List<CourtSearchResponse> results = new ArrayList<>();

        for (Court court : candidates) {
            if (!Boolean.TRUE.equals(court.getActive())) {
                continue;
            }

            CourtPrice price = prices
                    .findByCourtTypeIdAndActiveTrue(
                            court.getRoom().getCourtType().getId())
                    .orElse(null);

            // Không trả sân chưa có bảng giá hoạt động.
            if (price == null) {
                continue;
            }

            if (request.startTime().isBefore(price.getOpeningTime())
                    || request.endTime().isAfter(
                            price.getClosingTime())) {
                continue;
            }

            if (maintenance.existsActiveMaintenanceOverlap(
                    court.getId(), start, end)) {
                continue;
            }

            boolean bookingOverlap = bookings
                    .findByCourtIdAndBookingDateAndStatusNot(
                            court.getId(),
                            request.date(),
                            "CANCELLED")
                    .stream()
                    .filter(b -> !"NO_SHOW".equals(b.getStatus()))
                    .anyMatch(b -> overlaps(
                            request.startTime(),
                            request.endTime(),
                            b.getStartTime(),
                            b.getEndTime()));

            if (bookingOverlap) {
                continue;
            }

            if (hasDailyVisitorOverlap(
                    court.getId(), request, daySessions)) {
                continue;
            }

            long totalPrice = calculatePrice(
                    price,
                    request.startTime(),
                    request.endTime());

            if (request.maxTotalPrice() != null
                    && totalPrice > request.maxTotalPrice()) {
                continue;
            }

            results.add(new CourtSearchResponse(
                    court.getId(),
                    court.getName(),
                    request.date(),
                    request.startTime(),
                    request.endTime(),
                    duration,
                    totalPrice
            ));
        }

        results.sort(
                java.util.Comparator
                        .comparingLong(CourtSearchResponse::totalPrice)
                        .thenComparing(CourtSearchResponse::courtId)
        );

        return results;
    }

    private void validate(CourtSearchRequest request) {
        if (request == null
                || request.date() == null
                || request.startTime() == null
                || request.endTime() == null) {
            throw badRequest("Vui lòng cung cấp ngày và khung giờ");
        }

        if (request.courtId() != null && request.courtId() <= 0) {
            throw badRequest("ID sân phải lớn hơn 0");
        }

        if (request.maxTotalPrice() != null
                && request.maxTotalPrice() < 0) {
            throw badRequest("Ngân sách không được âm");
        }

        if (!isSlotTime(request.startTime())
                || !isSlotTime(request.endTime())) {
            throw badRequest(
                    "Giờ chơi phải ở mốc :00 hoặc :30, không có giây");
        }

        if (!request.endTime().isAfter(request.startTime())) {
            throw badRequest("Giờ kết thúc phải sau giờ bắt đầu");
        }

        long minutes = Duration.between(
                request.startTime(), request.endTime()).toMinutes();

        if (minutes < 60 || minutes % 30 != 0) {
            throw badRequest(
                    "Chơi tối thiểu 60 phút, tăng theo từng 30 phút");
        }

        // Dùng cùng thời gian hệ thống như BookingController.
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        if (!LocalDateTime.of(
                request.date(), request.startTime()).isAfter(now)) {
            throw badRequest("Không tìm khung giờ đã qua");
        }

        LocalDate sunday = today.with(
                TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        LocalDate lastBookingDate =
                today.getDayOfWeek() == DayOfWeek.MONDAY
                        ? sunday
                        : sunday.plusWeeks(1);

        if (request.date().isAfter(lastBookingDate)) {
            throw badRequest(
                    "Ngày tìm vượt quá giới hạn đặt sân hiện tại");
        }
    }

    private boolean isSlotTime(LocalTime time) {
        return (time.getMinute() == 0 || time.getMinute() == 30)
                && time.getSecond() == 0
                && time.getNano() == 0;
    }

    private boolean overlaps(
            LocalTime start,
            LocalTime end,
            LocalTime otherStart,
            LocalTime otherEnd) {

        return start.isBefore(otherEnd)
                && end.isAfter(otherStart);
    }

    private boolean hasDailyVisitorOverlap(
            Long courtId,
            CourtSearchRequest request,
            List<DailyVisitorSession> daySessions) {

        // Xét các buổi đã sinh cho ngày đang tìm.
        boolean sessionOverlap = daySessions.stream()
                .filter(s -> s.getSchedule().getCourt()
                        .getId().equals(courtId))
                .filter(s -> !"CANCELLED".equals(s.getStatus()))
                .anyMatch(s -> overlaps(
                        request.startTime(),
                        request.endTime(),
                        s.getStartTime(),
                        s.getEndTime()));

        if (sessionOverlap) {
            return true;
        }

        // Lịch hoạt động nhưng chưa sinh session vẫn giữ khung giờ.
        List<DailyVisitorSchedule> activeSchedules =
                schedules.findByCourtIdAndActiveTrue(courtId);

        for (DailyVisitorSchedule schedule : activeSchedules) {
            boolean alreadyGenerated = daySessions.stream()
                    .anyMatch(s -> s.getSchedule().getId()
                            .equals(schedule.getId()));

            if (!alreadyGenerated && overlaps(
                    request.startTime(),
                    request.endTime(),
                    schedule.getStartTime(),
                    schedule.getEndTime())) {
                return true;
            }
        }

        return false;
    }

    private long calculatePrice(
            CourtPrice price,
            LocalTime start,
            LocalTime end) {

        LocalTime peakStart = price.getPeakStartTime();

        long normalMinutes = 0;
        long peakMinutes = 0;

        if (start.isBefore(peakStart)) {
            LocalTime normalEnd = end.isBefore(peakStart)
                    ? end : peakStart;

            normalMinutes =
                    Duration.between(start, normalEnd).toMinutes();
        }

        if (end.isAfter(peakStart)) {
            LocalTime actualPeakStart = start.isAfter(peakStart)
                    ? start : peakStart;

            peakMinutes =
                    Duration.between(actualPeakStart, end).toMinutes();
        }

        return price.getNormalPricePerHour() * normalMinutes / 60
                + price.getPeakPricePerHour() * peakMinutes / 60;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(
                HttpStatus.BAD_REQUEST, message);
    }
}