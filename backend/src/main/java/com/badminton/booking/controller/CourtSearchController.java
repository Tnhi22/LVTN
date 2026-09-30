package com.badminton.booking.controller;

import com.badminton.booking.dto.CourtSearchRequest;
import com.badminton.booking.dto.CourtSearchResponse;
import com.badminton.booking.service.CourtSearchService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/court-search")
public class CourtSearchController {

    private final CourtSearchService courtSearchService;

    public CourtSearchController(
            CourtSearchService courtSearchService) {
        this.courtSearchService = courtSearchService;
    }

    @PostMapping
    public List<CourtSearchResponse> search(
            @Valid @RequestBody CourtSearchRequest request) {

        return courtSearchService.search(request);
    }
}