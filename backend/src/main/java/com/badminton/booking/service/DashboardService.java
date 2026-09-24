package com.badminton.booking.service;

import com.badminton.booking.dto.InventoryDashboardItem;
import com.badminton.booking.dto.InventoryDashboardSummary;
import com.badminton.booking.dto.MaintenanceDashboardItem;
import com.badminton.booking.dto.MaintenanceDashboardSummary;
import com.badminton.booking.dto.TodayActivitySummary;
import com.badminton.booking.entity.Court;
import com.badminton.booking.entity.CourtMaintenance;
import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.entity.NormalBooking;
import com.badminton.booking.entity.Product;
import com.badminton.booking.repository.CourtMaintenanceRepository;
import com.badminton.booking.repository.CourtRepository;
import com.badminton.booking.repository.DailyVisitorParticipantRepository;
import com.badminton.booking.repository.DailyVisitorSessionRepository;
import com.badminton.booking.repository.NormalBookingRepository;
import com.badminton.booking.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DashboardService {

    private final ProductRepository productRepository;
    private final NormalBookingRepository
        normalBookingRepository;

    private final DailyVisitorSessionRepository
            dailyVisitorSessionRepository;

    private final DailyVisitorParticipantRepository
            dailyVisitorParticipantRepository;
        private final CourtRepository courtRepository;

        private final CourtMaintenanceRepository
                courtMaintenanceRepository;

        public DashboardService(
                ProductRepository productRepository,
                NormalBookingRepository normalBookingRepository,
                DailyVisitorSessionRepository dailyVisitorSessionRepository,
                DailyVisitorParticipantRepository dailyVisitorParticipantRepository,
                CourtRepository courtRepository,
                CourtMaintenanceRepository courtMaintenanceRepository) {

        this.productRepository = productRepository;
        this.normalBookingRepository =
                normalBookingRepository;
        this.dailyVisitorSessionRepository =
                dailyVisitorSessionRepository;
        this.dailyVisitorParticipantRepository =
                dailyVisitorParticipantRepository;
        this.courtRepository = courtRepository;
        this.courtMaintenanceRepository =
                courtMaintenanceRepository;
        }

    @Transactional(readOnly = true)
    public List<InventoryDashboardItem>
    getInventoryDashboard() {

        return productRepository.findAll()
                .stream()
                .sorted(
                        Comparator.comparing(
                                Product::getName,
                                String.CASE_INSENSITIVE_ORDER
                        )
                )
                .map(this::toInventoryDashboardItem)
                .toList();
    }

    private InventoryDashboardItem
    toInventoryDashboardItem(Product product) {

        int physicalStock =
                product.getStockQuantityTubes() == null
                        ? 0
                        : product.getStockQuantityTubes();

        int reservedStock =
                product.getReservedQuantityTubes() == null
                        ? 0
                        : product.getReservedQuantityTubes();

        int minimumStock =
                product.getMinimumStockTubes() == null
                        ? 0
                        : product.getMinimumStockTubes();

        int targetStock =
                product.getTargetStockTubes() == null
                        ? minimumStock
                        : product.getTargetStockTubes();

        int availableStock =
                Math.max(
                        physicalStock - reservedStock,
                        0
                );

        String stockStatus;

        if (availableStock <= 0) {
            stockStatus = "OUT_OF_STOCK";
        } else if (availableStock <= minimumStock) {
            stockStatus = "LOW_STOCK";
        } else {
            stockStatus = "IN_STOCK";
        }

        int suggestedImportTubes = 0;

        // Chỉ đề xuất nhập khi đã chạm mức tối thiểu
        if (availableStock <= minimumStock) {
            suggestedImportTubes =
                    Math.max(
                            targetStock - availableStock,
                            0
                    );
        }

        InventoryDashboardItem item =
                new InventoryDashboardItem();

        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setBrand(product.getBrand());
        item.setStockQuantityTubes(physicalStock);
        item.setReservedQuantityTubes(reservedStock);
        item.setAvailableQuantityTubes(availableStock);
        item.setMinimumStockTubes(minimumStock);
        item.setTargetStockTubes(targetStock);
        item.setStockStatus(stockStatus);
        item.setSuggestedImportTubes(
                suggestedImportTubes
        );

        return item;
    }


    @Transactional(readOnly = true)
    public InventoryDashboardSummary
    getInventorySummary() {

        List<InventoryDashboardItem> items =
                getInventoryDashboard();

        int totalProducts = items.size();

        int totalStockTubes = items.stream()
                .mapToInt(
                        InventoryDashboardItem
                                ::getStockQuantityTubes
                )
                .sum();

        int totalReservedTubes = items.stream()
                .mapToInt(
                        InventoryDashboardItem
                                ::getReservedQuantityTubes
                )
                .sum();

        int totalAvailableTubes = items.stream()
                .mapToInt(
                        InventoryDashboardItem
                                ::getAvailableQuantityTubes
                )
                .sum();

        int lowStockProducts =
                (int) items.stream()
                        .filter(item ->
                                "LOW_STOCK".equals(
                                        item.getStockStatus()
                                )
                        )
                        .count();

        int outOfStockProducts =
                (int) items.stream()
                        .filter(item ->
                                "OUT_OF_STOCK".equals(
                                        item.getStockStatus()
                                )
                        )
                        .count();

        InventoryDashboardSummary summary =
                new InventoryDashboardSummary();

        summary.setTotalProducts(totalProducts);
        summary.setTotalStockTubes(totalStockTubes);
        summary.setTotalReservedTubes(totalReservedTubes);
        summary.setTotalAvailableTubes(totalAvailableTubes);
        summary.setLowStockProducts(lowStockProducts);
        summary.setOutOfStockProducts(
                outOfStockProducts
        );

        return summary;
    }

    @Transactional(readOnly = true)
    public TodayActivitySummary
    getTodayActivitySummary() {

        LocalDate today = LocalDate.now();

        List<NormalBooking> bookings =
                normalBookingRepository
                        .findByBookingDate(today);

        int pendingBookings = 0;
        int checkedInBookings = 0;
        int cancelledBookings = 0;
        int noShowBookings = 0;

        long normalBookingRevenue = 0L;

        for (NormalBooking booking : bookings) {

            String status = booking.getStatus();

            if ("PENDING".equals(status)
                    || "NO_SHOW_PENDING".equals(status)) {

                pendingBookings++;

            } else if ("CHECKED_IN".equals(status)) {

                checkedInBookings++;

                if (booking.getTotalAmount() != null) {
                    normalBookingRevenue +=
                            booking.getTotalAmount();
                }

            } else if ("CANCELLED".equals(status)) {

                cancelledBookings++;

            } else if ("NO_SHOW".equals(status)) {

                noShowBookings++;
            }
        }

        List<DailyVisitorSession> sessions =
                dailyVisitorSessionRepository
                        .findBySessionDate(today);

        int openDailyVisitorSessions = 0;
        int cancelledDailyVisitorSessions = 0;
        long checkedInSlots = 0L;

        for (DailyVisitorSession session : sessions) {

            String status = session.getStatus();

            if ("OPEN".equals(status)
                    || "FULL".equals(status)) {

                openDailyVisitorSessions++;

            } else if ("CANCELLED".equals(status)) {

                cancelledDailyVisitorSessions++;
            }

            Long sessionCheckedInSlots =
                    dailyVisitorParticipantRepository
                            .getCheckedInSlots(
                                    session.getId()
                            );

            if (sessionCheckedInSlots != null) {
                checkedInSlots +=
                        sessionCheckedInSlots;
            }
        }

        TodayActivitySummary summary =
                new TodayActivitySummary();

        summary.setDate(today);

        summary.setTotalNormalBookings(
                bookings.size()
        );
        summary.setPendingNormalBookings(
                pendingBookings
        );
        summary.setCheckedInNormalBookings(
                checkedInBookings
        );
        summary.setCancelledNormalBookings(
                cancelledBookings
        );
        summary.setNoShowNormalBookings(
                noShowBookings
        );
        summary.setNormalBookingRevenue(
                normalBookingRevenue
        );

        summary.setTotalDailyVisitorSessions(
                sessions.size()
        );
        summary.setOpenDailyVisitorSessions(
                openDailyVisitorSessions
        );
        summary.setCancelledDailyVisitorSessions(
                cancelledDailyVisitorSessions
        );
        summary.setDailyVisitorCheckedInSlots(
                checkedInSlots
        );

        return summary;
    }

    @Transactional(readOnly = true)
public MaintenanceDashboardSummary
getMaintenanceDashboard() {

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime warningLimit =
            now.plusDays(30);

    List<CourtMaintenance> activeRecords =
            courtMaintenanceRepository
                    .findByStatusInOrderByStartTimeAsc(
                            List.of(
                                    "SCHEDULED",
                                    "IN_PROGRESS"
                            )
                    );

    int inProgressCount =
            (int) activeRecords.stream()
                    .filter(record ->
                            "IN_PROGRESS".equals(
                                    record.getStatus()
                            )
                    )
                    .count();

    int scheduledCount =
            (int) activeRecords.stream()
                    .filter(record ->
                            "SCHEDULED".equals(
                                    record.getStatus()
                            )
                    )
                    .count();

    List<MaintenanceDashboardItem>
            activeMaintenances =
            activeRecords.stream()
                    .map(this::toMaintenanceItem)
                    .toList();

    List<Court> alertCourts =
            courtRepository.findAll()
                    .stream()
                    .filter(court ->
                            Boolean.TRUE.equals(
                                    court.getActive()
                            )
                    )
                    .filter(court ->
                            court.getNextMaintenanceAt()
                                    != null
                    )
                    .filter(court ->
                            !court.getNextMaintenanceAt()
                                    .isAfter(warningLimit)
                    )
                    .sorted(
                            Comparator.comparing(
                                    Court::getNextMaintenanceAt
                            )
                    )
                    .toList();

    List<MaintenanceDashboardItem>
            periodicAlerts =
            new ArrayList<>();

    int overdueCourtCount = 0;
    int dueSoonCourtCount = 0;

    for (Court court : alertCourts) {

        MaintenanceDashboardItem item =
                new MaintenanceDashboardItem();

        item.setCourtId(court.getId());
        item.setCourtName(court.getName());
        item.setMaintenanceIntervalMonths(
                court.getMaintenanceIntervalMonths()
        );
        item.setNextMaintenanceAt(
                court.getNextMaintenanceAt()
        );

        if (!court.getNextMaintenanceAt()
                .isAfter(now)) {

            item.setAlertStatus("OVERDUE");
            overdueCourtCount++;

        } else {

            item.setAlertStatus("DUE_SOON");
            dueSoonCourtCount++;
        }

        periodicAlerts.add(item);
    }

    MaintenanceDashboardSummary summary =
            new MaintenanceDashboardSummary();

    summary.setInProgressCount(
            inProgressCount
    );
    summary.setScheduledCount(
            scheduledCount
    );
    summary.setOverdueCourtCount(
            overdueCourtCount
    );
    summary.setDueSoonCourtCount(
            dueSoonCourtCount
    );
    summary.setActiveMaintenances(
            activeMaintenances
    );
    summary.setPeriodicMaintenanceAlerts(
            periodicAlerts
    );

    return summary;
}

        private MaintenanceDashboardItem
        toMaintenanceItem(
                CourtMaintenance maintenance) {

        Court court = maintenance.getCourt();

        MaintenanceDashboardItem item =
                new MaintenanceDashboardItem();

        item.setMaintenanceId(
                maintenance.getId()
        );
        item.setCourtId(court.getId());
        item.setCourtName(court.getName());

        item.setType(maintenance.getType());
        item.setStatus(maintenance.getStatus());
        item.setReason(maintenance.getReason());

        item.setStartTime(
                maintenance.getStartTime()
        );
        item.setEndTime(
                maintenance.getEndTime()
        );

        item.setMaintenanceIntervalMonths(
                court.getMaintenanceIntervalMonths()
        );
        item.setNextMaintenanceAt(
                court.getNextMaintenanceAt()
        );
        item.setAlertStatus(
                maintenance.getStatus()
        );

        return item;
        }
}
