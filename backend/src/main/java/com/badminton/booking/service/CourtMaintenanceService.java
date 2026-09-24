package com.badminton.booking.service;

import com.badminton.booking.dto.CourtMaintenanceRequest;
import com.badminton.booking.entity.Court;
import com.badminton.booking.entity.CourtMaintenance;
import com.badminton.booking.entity.User;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.CourtMaintenanceRepository;
import com.badminton.booking.repository.CourtRepository;
import com.badminton.booking.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.repository.NormalBookingRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class CourtMaintenanceService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of(
                    "SCHEDULED",
                    "EMERGENCY"
            );

    private final CourtMaintenanceRepository
            maintenanceRepository;

    private final CourtRepository courtRepository;
    private final UserRepository userRepository;
    private final NormalBookingRepository bookingRepository;
        private final BookingInventoryService bookingInventoryService;

        public CourtMaintenanceService(
                CourtMaintenanceRepository maintenanceRepository,
                CourtRepository courtRepository,
                UserRepository userRepository,
                NormalBookingRepository bookingRepository,
                BookingInventoryService bookingInventoryService) {

        this.maintenanceRepository = maintenanceRepository;
        this.courtRepository = courtRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.bookingInventoryService = bookingInventoryService;
        }

    @Transactional
    public CourtMaintenance createMaintenance(
            CourtMaintenanceRequest request,
            Long creatorId) {

        if (request == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Dữ liệu bảo trì không hợp lệ"
            );
        }

        if (request.getCourtId() == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn sân"
            );
        }

        User creator = userRepository
                .findById(creatorId)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy nhân viên"
                        )
                );

        String role = creator.getRole();

        if (!"STAFF".equals(role)
                && !"ADMIN".equals(role)) {

            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ STAFF hoặc ADMIN được báo bảo trì"
            );
        }

        Court court = courtRepository
                .findById(request.getCourtId())
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy sân"
                        )
                );

        if (!Boolean.TRUE.equals(court.getActive())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Sân đã ngừng hoạt động lâu dài"
            );
        }

        String type = request.getType();

        if (type == null || type.isBlank()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn loại bảo trì"
            );
        }

        type = type.trim().toUpperCase();

        if (!ALLOWED_TYPES.contains(type)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Loại bảo trì chỉ nhận SCHEDULED hoặc EMERGENCY"
            );
        }

        if (request.getReason() == null
                || request.getReason().isBlank()) {

            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Lý do bảo trì không được để trống"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime =
                request.getStartTime();

        // Sự cố đột xuất không gửi thời gian thì bắt đầu ngay
        if (startTime == null
                && "EMERGENCY".equals(type)) {

            startTime = now;
        }

        if (startTime == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập thời gian bắt đầu"
            );
        }

        // Bảo trì có kế hoạch không được đặt trong quá khứ
        if ("SCHEDULED".equals(type)
                && startTime.isBefore(now)) {

            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Thời gian bảo trì có kế hoạch không được ở quá khứ"
            );
        }

        LocalDateTime endTime =
                request.getEndTime();

        // Bảo trì có kế hoạch phải biết thời gian kết thúc
        if ("SCHEDULED".equals(type)
                && endTime == null) {

            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Bảo trì có kế hoạch phải có thời gian kết thúc"
            );
        }

        if (endTime != null
                && !endTime.isAfter(startTime)) {

            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Thời gian kết thúc phải sau thời gian bắt đầu"
            );
        }

        Long maintenanceCost =
                request.getMaintenanceCost();

        if (maintenanceCost == null) {
            maintenanceCost = 0L;
        }

        if (maintenanceCost < 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Chi phí bảo trì không được âm"
            );
        }

        LocalDateTime overlapEnd =
                endTime != null
                        ? endTime
                        : LocalDateTime.of(
                                9999,
                                12,
                                31,
                                23,
                                59
                        );

        boolean overlap =
                maintenanceRepository
                        .existsActiveMaintenanceOverlap(
                                court.getId(),
                                startTime,
                                overlapEnd
                        );

        if (overlap) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Sân đã có lịch bảo trì hoặc sự cố trùng thời gian"
            );
        }

        List<NormalBooking> affectedBookings =
        bookingRepository
                .findBookingsAffectedByMaintenance(
                        court.getId(),
                        startTime,
                        overlapEnd
                );

        /*
        * Bảo trì có kế hoạch không được làm ảnh hưởng
        * đến booking đã tồn tại.
        */
        if ("SCHEDULED".equals(type)
                && !affectedBookings.isEmpty()) {

        throw new BusinessException(
                HttpStatus.CONFLICT,
                "Khung giờ bảo trì đã có "
                        + affectedBookings.size()
                        + " booking, vui lòng chọn thời gian khác"
        );
        }

        /*
        * Sự cố đột xuất vẫn được ghi nhận.
        * Các booking chưa check-in bị ảnh hưởng sẽ được hủy.
        */
        if ("EMERGENCY".equals(type)) {

        for (NormalBooking booking : affectedBookings) {

                booking.setStatus("CANCELLED");
                booking.setCancelledAt(now);
                booking.setCancelledByStaffId(
                        creator.getId()
                );
                booking.setCancelledByStaffName(
                        creator.getFullName()
                );

                // Hoàn số ống cầu đang được booking giữ
                bookingInventoryService
                        .releaseReservation(booking);
        }

        bookingRepository.saveAll(
                affectedBookings
        );
        }

        CourtMaintenance maintenance =
                new CourtMaintenance();

        maintenance.setCourt(court);
        maintenance.setType(type);
        maintenance.setReason(
                request.getReason().trim()
        );
        maintenance.setStartTime(startTime);
        maintenance.setEndTime(endTime);
        maintenance.setMaintenanceCost(
                maintenanceCost
        );
        maintenance.setCreatedBy(creator);

        if (!startTime.isAfter(now)) {
            maintenance.setStatus("IN_PROGRESS");
        } else {
            maintenance.setStatus("SCHEDULED");
        }

        return maintenanceRepository.save(
                maintenance
        );
    }

    @Transactional(readOnly = true)
    public List<CourtMaintenance>
    getCourtMaintenanceHistory(Long courtId) {

        if (!courtRepository.existsById(courtId)) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy sân"
            );
        }

        return maintenanceRepository
                .findByCourtIdOrderByStartTimeDesc(
                        courtId
                );
    }

    @Transactional
public CourtMaintenance completeMaintenance(
        Long maintenanceId,
        Long staffId) {

    User staff = userRepository.findById(staffId)
            .orElseThrow(() -> new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy nhân viên"
            ));

    if (!"STAFF".equals(staff.getRole())
            && !"ADMIN".equals(staff.getRole())) {

        throw new BusinessException(
                HttpStatus.FORBIDDEN,
                "Chỉ STAFF hoặc ADMIN được hoàn thành bảo trì"
        );
    }

    CourtMaintenance maintenance =
            maintenanceRepository.findById(maintenanceId)
                    .orElseThrow(() -> new BusinessException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy lịch bảo trì hoặc sự cố"
                    ));

    if ("COMPLETED".equals(maintenance.getStatus())) {
        throw new BusinessException(
                HttpStatus.CONFLICT,
                "Bảo trì hoặc sự cố đã hoàn thành trước đó"
        );
    }

    if ("CANCELLED".equals(maintenance.getStatus())) {
        throw new BusinessException(
                HttpStatus.CONFLICT,
                "Lịch bảo trì hoặc sự cố đã bị hủy"
        );
    }

    LocalDateTime now = LocalDateTime.now();

    maintenance.setStatus("COMPLETED");
    maintenance.setCompletedAt(now);

    Court court = maintenance.getCourt();

        Integer intervalMonths =
                court.getMaintenanceIntervalMonths();

        if (intervalMonths != null
                && intervalMonths > 0) {

        court.setNextMaintenanceAt(
                now.plusMonths(intervalMonths)
        );

        courtRepository.save(court);
        }

    /*
     * Với sự cố EMERGENCY chưa có giờ kết thúc dự kiến,
     * ghi nhận thời điểm kết thúc thực tế.
     */
    if (maintenance.getEndTime() == null) {
        maintenance.setEndTime(now);
    }

    return maintenanceRepository.save(maintenance);
}

        @Transactional
        public CourtMaintenance cancelMaintenance(
                Long maintenanceId,
                Long staffId) {

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhân viên"
                ));

        if (!"STAFF".equals(staff.getRole())
                && !"ADMIN".equals(staff.getRole())) {

                throw new BusinessException(
                        HttpStatus.FORBIDDEN,
                        "Chỉ STAFF hoặc ADMIN được hủy bảo trì"
                );
        }

        CourtMaintenance maintenance =
                maintenanceRepository.findById(maintenanceId)
                        .orElseThrow(() -> new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy lịch bảo trì hoặc sự cố"
                        ));

        if ("CANCELLED".equals(maintenance.getStatus())) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Lịch bảo trì hoặc sự cố đã bị hủy trước đó"
                );
        }

        if ("COMPLETED".equals(maintenance.getStatus())) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Bảo trì đã hoàn thành nên không thể hủy"
                );
        }

        maintenance.setStatus("CANCELLED");

        return maintenanceRepository.save(maintenance);
        }
}