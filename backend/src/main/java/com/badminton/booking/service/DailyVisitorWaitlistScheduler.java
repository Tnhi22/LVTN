package com.badminton.booking.service;

import com.badminton.booking.entity.WaitlistStatus;
import com.badminton.booking.repository.DailyVisitorWaitlistRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@EnableScheduling
public class DailyVisitorWaitlistScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(DailyVisitorWaitlistScheduler.class);

    private final DailyVisitorWaitlistRepository waitlistRepository;
    private final DailyVisitorWaitlistService waitlistService;

    public DailyVisitorWaitlistScheduler(
            DailyVisitorWaitlistRepository waitlistRepository,
            DailyVisitorWaitlistService waitlistService) {

        this.waitlistRepository = waitlistRepository;
        this.waitlistService = waitlistService;
    }

    // Chạy lại 5 giây sau khi lần xử lý trước hoàn tất.
    @Scheduled(
            fixedDelayString =
                    "${app.daily-visitor.waitlist.scan-delay-ms:5000}"
    )
    public void processWaitlists() {

        List<Long> sessionIds =
                waitlistRepository.findSessionIdsByStatuses(
                        List.of(
                                WaitlistStatus.WAITING,
                                WaitlistStatus.OFFERED
                        )
                );

        for (Long sessionId : sessionIds) {
            try {
                // Mỗi buổi được xử lý trong transaction riêng.
                waitlistService.processSession(sessionId);
            } catch (Exception exception) {
                log.error(
                        "Không thể xử lý danh sách chờ của buổi {}",
                        sessionId,
                        exception
                );
            }
        }
    }
}