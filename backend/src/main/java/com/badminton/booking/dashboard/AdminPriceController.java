package com.badminton.booking.dashboard;

import com.badminton.booking.entity.CourtPrice;
import com.badminton.booking.entity.DailyVisitorSchedule;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.CourtPriceRepository;
import com.badminton.booking.repository.DailyVisitorScheduleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/admin/prices")
public class AdminPriceController {

    private final CourtPriceRepository courtPriceRepository;
    private final DailyVisitorScheduleRepository scheduleRepository;

    public AdminPriceController(
            CourtPriceRepository courtPriceRepository,
            DailyVisitorScheduleRepository scheduleRepository) {
        this.courtPriceRepository = courtPriceRepository;
        this.scheduleRepository = scheduleRepository;
    }

    public record CourtPriceResponse(
            Long id,
            Long courtTypeId,
            String courtTypeName,
            LocalTime openingTime,
            LocalTime peakStartTime,
            LocalTime closingTime,
            Long normalPricePerHour,
            Long peakPricePerHour,
            Boolean active
    ) {}

    public record CourtPriceRequest(
            Long normalPricePerHour,
            Long peakPricePerHour
    ) {}

    public record DailyPriceResponse(
            Long scheduleId,
            String skillLevel,
            Long fixedFee
    ) {}

    public record DailyPriceRequest(Long fixedFee) {}

    @GetMapping("/courts")
    public List<CourtPriceResponse> getCourtPrices() {
        return courtPriceRepository.findAll().stream()
                .map(this::toCourtPriceResponse)
                .toList();
    }

    @PutMapping("/courts/{priceId}")
    @Transactional
    public CourtPriceResponse updateCourtPrice(
            @PathVariable Long priceId,
            @RequestBody CourtPriceRequest request) {

        if (request == null
                || request.normalPricePerHour() == null
                || request.normalPricePerHour() <= 0
                || request.peakPricePerHour() == null
                || request.peakPricePerHour() <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Giá giờ thường và giờ cao điểm phải lớn hơn 0"
            );
        }

        CourtPrice price = courtPriceRepository.findById(priceId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy bảng giá sân"
                ));

        price.setNormalPricePerHour(request.normalPricePerHour());
        price.setPeakPricePerHour(request.peakPricePerHour());

        return toCourtPriceResponse(courtPriceRepository.save(price));
    }

    @GetMapping("/daily-visitor")
    public List<DailyPriceResponse> getDailyPrices() {
        return scheduleRepository.findAll().stream()
                .map(schedule -> new DailyPriceResponse(
                        schedule.getId(),
                        schedule.getSkillLevel(),
                        schedule.getFixedFee()
                ))
                .toList();
    }

    @PutMapping("/daily-visitor/{skillLevel}")
    @Transactional
    public List<DailyPriceResponse> updateDailyPrice(
            @PathVariable String skillLevel,
            @RequestBody DailyPriceRequest request) {

        String level = skillLevel.toUpperCase(Locale.ROOT);

        if (!List.of("TBY", "TB", "TB+").contains(level)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Trình độ phải là TBY, TB hoặc TB+"
            );
        }

        if (request == null
                || request.fixedFee() == null
                || request.fixedFee() <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Giá Daily Visitor phải lớn hơn 0"
            );
        }

        List<DailyVisitorSchedule> schedules = scheduleRepository.findAll()
                .stream()
                .filter(s -> level.equalsIgnoreCase(s.getSkillLevel()))
                .toList();

        if (schedules.isEmpty()) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy lịch Daily Visitor cho trình độ " + level
            );
        }

        for (DailyVisitorSchedule schedule : schedules) {
            schedule.setFixedFee(request.fixedFee());
        }

        return scheduleRepository.saveAll(schedules).stream()
                .map(schedule -> new DailyPriceResponse(
                        schedule.getId(),
                        schedule.getSkillLevel(),
                        schedule.getFixedFee()
                ))
                .toList();
    }

    private CourtPriceResponse toCourtPriceResponse(CourtPrice price) {
        return new CourtPriceResponse(
                price.getId(),
                price.getCourtType().getId(),
                price.getCourtType().getName(),
                price.getOpeningTime(),
                price.getPeakStartTime(),
                price.getClosingTime(),
                price.getNormalPricePerHour(),
                price.getPeakPricePerHour(),
                price.getActive()
        );
    }
}