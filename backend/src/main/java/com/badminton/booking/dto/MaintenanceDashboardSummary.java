package com.badminton.booking.dto;
import com.badminton.booking.dto.MaintenanceDashboardItem;
import com.badminton.booking.dto.MaintenanceDashboardSummary;
import java.util.List;

public class MaintenanceDashboardSummary {

    private Integer inProgressCount;
    private Integer scheduledCount;
    private Integer overdueCourtCount;
    private Integer dueSoonCourtCount;

    private List<MaintenanceDashboardItem>
            activeMaintenances;

    private List<MaintenanceDashboardItem>
            periodicMaintenanceAlerts;

    public MaintenanceDashboardSummary() {
    }

    public Integer getInProgressCount() {
        return inProgressCount;
    }

    public void setInProgressCount(
            Integer inProgressCount) {
        this.inProgressCount = inProgressCount;
    }

    public Integer getScheduledCount() {
        return scheduledCount;
    }

    public void setScheduledCount(
            Integer scheduledCount) {
        this.scheduledCount = scheduledCount;
    }

    public Integer getOverdueCourtCount() {
        return overdueCourtCount;
    }

    public void setOverdueCourtCount(
            Integer overdueCourtCount) {
        this.overdueCourtCount = overdueCourtCount;
    }

    public Integer getDueSoonCourtCount() {
        return dueSoonCourtCount;
    }

    public void setDueSoonCourtCount(
            Integer dueSoonCourtCount) {
        this.dueSoonCourtCount = dueSoonCourtCount;
    }

    public List<MaintenanceDashboardItem>
    getActiveMaintenances() {
        return activeMaintenances;
    }

    public void setActiveMaintenances(
            List<MaintenanceDashboardItem>
                    activeMaintenances) {
        this.activeMaintenances =
                activeMaintenances;
    }

    public List<MaintenanceDashboardItem>
    getPeriodicMaintenanceAlerts() {
        return periodicMaintenanceAlerts;
    }

    public void setPeriodicMaintenanceAlerts(
            List<MaintenanceDashboardItem>
                    periodicMaintenanceAlerts) {
        this.periodicMaintenanceAlerts =
                periodicMaintenanceAlerts;
    }
}