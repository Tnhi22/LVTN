package com.badminton.booking.controller;

import com.badminton.booking.dto.MaintenanceCompleteRequest;
import com.badminton.booking.entity.CourtMaintenance;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.CourtMaintenanceRepository;
import com.badminton.booking.service.CourtMaintenanceService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/maintenances")
public class AdminMaintenanceController {
    private final CourtMaintenanceRepository repository;
    private final CourtMaintenanceService service;
    @PersistenceContext
    private EntityManager entityManager;
    public AdminMaintenanceController(CourtMaintenanceRepository repository, CourtMaintenanceService service) {
        this.repository = repository;
        this.service = service;
    }
    private void requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập");
        }
        if (authentication.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ ADMIN được duyệt hoàn tất bảo trì");
        }
    }
    @GetMapping
    public List<CourtMaintenance> list(Authentication authentication) {
        requireAdmin(authentication);
        return repository.findAll(Sort.by(Sort.Direction.DESC, "startTime"));
    }
    @Transactional
    @PatchMapping("/{id}/complete")
    public CourtMaintenance complete(@PathVariable Long id,
            @Valid @RequestBody MaintenanceCompleteRequest request,
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        requireAdmin(authentication);
        if (jwt == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập");
        CourtMaintenance maintenance = entityManager.find(CourtMaintenance.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (maintenance == null) throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch bảo trì");
        if (!"SCHEDULED".equals(maintenance.getStatus()) && !"IN_PROGRESS".equals(maintenance.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Lịch đã hoàn thành hoặc đã hủy; không thể duyệt lại");
        }
        if (maintenance.getStartTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Chưa đến giờ bắt đầu bảo trì");
        }
        // Chi phí, nội dung sửa chữa và hoàn tất được lưu trong cùng transaction.
        maintenance.setMaintenanceCost(request.maintenanceCost());
        maintenance.setRepairDetail(request.repairDetail().trim());
        return service.completeMaintenance(id, Long.valueOf(jwt.getSubject()));
    }
}
