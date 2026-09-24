package com.badminton.booking.controller;

import com.badminton.booking.dto.ChangeShuttlecockProductRequest;
import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.entity.User;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.UserRepository;
import com.badminton.booking.service.DailyVisitorSessionService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/daily-visitor-sessions")
public class DailyVisitorInventoryController {

    private final DailyVisitorSessionService
            sessionService;

    private final UserRepository userRepository;

    public DailyVisitorInventoryController(
            DailyVisitorSessionService sessionService,
            UserRepository userRepository) {

        this.sessionService = sessionService;
        this.userRepository = userRepository;
    }

    // STAFF hoặc ADMIN đổi loại cầu trước khi xuất kho
    @PutMapping(
            "/{sessionId}/shuttlecock-product"
    )
    public DailyVisitorSession changeShuttlecockProduct(
            @PathVariable Long sessionId,
            @RequestParam Long staffId,
            @RequestBody
            ChangeShuttlecockProductRequest request) {

        User staff = userRepository.findById(staffId)
                .orElseThrow(() ->
                        new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy nhân viên"
                        )
                );

        String role = staff.getRole();

        if (!"STAFF".equals(role)
                && !"ADMIN".equals(role)) {

            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ STAFF hoặc ADMIN được đổi loại cầu"
            );
        }

        return sessionService
                .changeShuttlecockProduct(
                        sessionId,
                        request.getProductId()
                );
    }
}