package com.badminton.booking.service;

import com.badminton.booking.entity.CourtMaintenance;
import com.badminton.booking.repository.CourtMaintenanceRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CourtMaintenanceScheduler {

    private final CourtMaintenanceRepository
            maintenanceRepository;

    public CourtMaintenanceScheduler(
            CourtMaintenanceRepository maintenanceRepository) {

        this.maintenanceRepository =
                maintenanceRepository;
    }

    /*
     * Chạy mỗi 60 giây.
     * Khi đến giờ bắt đầu, lịch bảo trì sẽ tự động
     * chuyển từ SCHEDULED sang IN_PROGRESS.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void startScheduledMaintenances() {

        LocalDateTime now = LocalDateTime.now();

        List<CourtMaintenance> scheduledMaintenances =
                maintenanceRepository
                        .findByStatusInOrderByStartTimeAsc(
                                List.of("SCHEDULED")
                        );

        for (CourtMaintenance maintenance
                : scheduledMaintenances) {

            if (!maintenance.getStartTime()
                    .isAfter(now)) {

                maintenance.setStatus(
                        "IN_PROGRESS"
                );
            }
        }

        maintenanceRepository.saveAll(
                scheduledMaintenances
        );
    }
}