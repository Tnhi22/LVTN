package com.badminton.booking.controller;

import com.badminton.booking.entity.*;
import com.badminton.booking.repository.DailyVisitorScheduleRepository;
import com.badminton.booking.repository.ProductRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Danh mục công khai: không chứa giá vốn, nhà cung cấp hoặc dữ liệu người dùng. */
@RestController
@RequestMapping("/api/courts/catalogue")
public class CustomerCatalogueController {

  private final ProductRepository products;
  private final DailyVisitorScheduleRepository schedules;

  public CustomerCatalogueController(
    ProductRepository products,
    DailyVisitorScheduleRepository schedules
  ) {
    this.products = products;
    this.schedules = schedules;
  }

  public record ProductRow(
    Long id,
    String name,
    String brand,
    String imageUrl,
    String detail,
    Integer piecesPerTube,
    Long tubePrice,
    Long piecePrice,
    Integer availableQuantityTubes
  ) {}

  public record DailyRow(
    Long id,
    Long courtId,
    String courtName,
    String skillLevel,
    java.time.LocalTime startTime,
    java.time.LocalTime endTime,
    Long fixedFee,
    Integer maxParticipants
  ) {}

  public record Catalogue(List<ProductRow> products, List<DailyRow> dailySchedules) {}

  @GetMapping
  @Transactional(readOnly = true)
  public Catalogue getCatalogue() {
    return new Catalogue(
      products
        .findByActiveTrueOrderByNameAsc()
        .stream()
        .map(p ->
          new ProductRow(
            p.getId(),
            p.getName(),
            p.getBrand(),
            p.getImageUrl(),
            p.getDetail(),
            p.getPiecesPerTube(),
            p.getTubePrice(),
            p.getPiecePrice(),
            p.getAvailableQuantityTubes()
          )
        )
        .toList(),
      schedules
        .findAll()
        .stream()
        .filter(s -> Boolean.TRUE.equals(s.getActive()))
        .map(s ->
          new DailyRow(
            s.getId(),
            s.getCourt().getId(),
            s.getCourt().getName(),
            s.getSkillLevel(),
            s.getStartTime(),
            s.getEndTime(),
            s.getFixedFee(),
            s.getMaxParticipants()
          )
        )
        .toList()
    );
  }
}
