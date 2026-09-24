package com.badminton.booking.service;

import com.badminton.booking.entity.DailyVisitorSchedule;
import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.entity.Product;
import com.badminton.booking.repository.DailyVisitorScheduleRepository;
import com.badminton.booking.repository.DailyVisitorSessionRepository;
import com.badminton.booking.repository.ProductRepository;

import jakarta.annotation.PostConstruct;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.badminton.booking.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DailyVisitorSessionService {

    private static final Long DEFAULT_PRODUCT_ID = 1L;

    private final DailyVisitorScheduleRepository
            scheduleRepository;

    private final DailyVisitorSessionRepository
            sessionRepository;

    private final ProductRepository productRepository;

    public DailyVisitorSessionService(
            DailyVisitorScheduleRepository scheduleRepository,
            DailyVisitorSessionRepository sessionRepository,
            ProductRepository productRepository) {

        this.scheduleRepository = scheduleRepository;
        this.sessionRepository = sessionRepository;
        this.productRepository = productRepository;
    }

    // Tạo session cho một ngày cụ thể
    @Transactional
    public List<DailyVisitorSession> generateSessions(
            LocalDate date) {

        List<DailyVisitorSchedule> schedules =
                scheduleRepository.findByActiveTrue();

        List<DailyVisitorSession> sessions =
                new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();

        // Cầu mặc định: Hải Yến S70
        Product defaultProduct = productRepository
                .findById(DEFAULT_PRODUCT_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy sản phẩm cầu mặc định id = "
                                + DEFAULT_PRODUCT_ID
                ));

        if (!Boolean.TRUE.equals(defaultProduct.getActive())) {
            throw new IllegalStateException(
                    "Sản phẩm cầu mặc định đang ngừng bán"
            );
        }

        for (DailyVisitorSchedule schedule : schedules) {

            boolean exists =
                    sessionRepository
                            .existsByScheduleIdAndSessionDate(
                                    schedule.getId(),
                                    date
                            );

            if (exists) {
                continue;
            }

            DailyVisitorSession session =
                    new DailyVisitorSession();

            session.setSchedule(schedule);
            session.setSessionDate(date);
            session.setStartTime(
                    schedule.getStartTime()
            );
            session.setEndTime(
                    schedule.getEndTime()
            );
            session.setMinParticipants(
                    schedule.getMinParticipants()
            );
            session.setMaxParticipants(
                    schedule.getMaxParticipants()
            );

            // Mặc định sử dụng Hải Yến S70
            session.setShuttlecockProduct(defaultProduct);
            session.setShuttlecockQuantityTubes(1);
            session.setShuttlecockIssued(false);

            LocalDateTime sessionEnd =
                    LocalDateTime.of(
                            date,
                            schedule.getEndTime()
                    );

            if (!sessionEnd.isAfter(now)) {
                session.setStatus("CLOSED");
            } else {
                session.setStatus("OPEN");
            }

            sessions.add(session);
        }

        return sessionRepository.saveAll(sessions);
    }

    // Khi backend khởi động:
    // bảo đảm có session hôm nay, ngày mai và ngày kia
    @PostConstruct
    public void initRollingSessions() {

        LocalDate today = LocalDate.now();

        generateSessions(today);
        generateSessions(today.plusDays(1));
        generateSessions(today.plusDays(2));
    }

    // Chạy mỗi ngày lúc 00:05
    @Scheduled(cron = "0 5 0 * * *")
    public void generateRollingSessions() {

        LocalDate today = LocalDate.now();

        generateSessions(today);
        generateSessions(today.plusDays(1));
        generateSessions(today.plusDays(2));
    }

    @Transactional
    public DailyVisitorSession changeShuttlecockProduct(
            Long sessionId,
            Long productId) {

        if (productId == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn loại cầu"
            );
        }

        DailyVisitorSession session =
                sessionRepository.findById(sessionId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        HttpStatus.NOT_FOUND,
                                        "Không tìm thấy session Daily Visitor"
                                )
                        );

        if (Boolean.TRUE.equals(
                session.getShuttlecockIssued())) {

            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Buổi chơi đã xuất cầu nên không thể đổi loại cầu"
            );
        }

        if ("CANCELLED".equals(session.getStatus())
                || "CLOSED".equals(session.getStatus())) {

            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Không thể đổi cầu cho session đã đóng hoặc đã hủy"
            );
        }

        Product product =
                productRepository.findById(productId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        HttpStatus.NOT_FOUND,
                                        "Không tìm thấy sản phẩm cầu"
                                )
                        );

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Sản phẩm cầu đang ngừng bán"
            );
        }

        int physicalStock =
                product.getStockQuantityTubes() == null
                        ? 0
                        : product.getStockQuantityTubes();

        int reservedStock =
                product.getReservedQuantityTubes() == null
                        ? 0
                        : product.getReservedQuantityTubes();

        if (physicalStock - reservedStock < 1) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Loại cầu này không còn tồn kho khả dụng"
            );
        }

    session.setShuttlecockProduct(product);
    session.setShuttlecockQuantityTubes(1);

        return sessionRepository.save(session);
    }
}