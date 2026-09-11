package com.badminton.booking.controller;

import com.badminton.booking.entity.CourtType;
import com.badminton.booking.repository.CourtTypeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/court-types")
public class CourtTypeController {

    private final CourtTypeRepository courtTypeRepository;

    public CourtTypeController(CourtTypeRepository courtTypeRepository) {
        this.courtTypeRepository = courtTypeRepository;
    }

    @GetMapping
    public List<CourtType> getAllCourtTypes() {
        return courtTypeRepository.findAll();
    }
}