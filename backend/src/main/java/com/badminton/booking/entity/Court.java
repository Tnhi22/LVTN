package com.badminton.booking.entity;
import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "courts")
public class Court {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @ManyToOne
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    // false = sân ngừng hoạt động lâu dài / không còn sử dụng
    // Maintenance tạm thời sẽ được quản lý bởi CourtMaintenance
    @Column(nullable = false)
    private Boolean active = true;

    // Chu kỳ bảo trì định kỳ, tính theo tháng
    @Column(name = "maintenance_interval_months")
    private Integer maintenanceIntervalMonths;

    // Ngày giờ dự kiến bảo trì tiếp theo
    @Column(name = "next_maintenance_at")
    private LocalDateTime nextMaintenanceAt;

    public Court() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }


    public Integer getMaintenanceIntervalMonths() {
    return maintenanceIntervalMonths;
    }

    public void setMaintenanceIntervalMonths(
            Integer maintenanceIntervalMonths) {
        this.maintenanceIntervalMonths =
                maintenanceIntervalMonths;
    }

    public LocalDateTime getNextMaintenanceAt() {
        return nextMaintenanceAt;
    }

    public void setNextMaintenanceAt(
            LocalDateTime nextMaintenanceAt) {
        this.nextMaintenanceAt =
                nextMaintenanceAt;
    }
}