package com.badminton.booking.controller;

import com.badminton.booking.entity.*;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Protected by the existing ADMIN-only /api/admin/prices rule. */
@RestController
@RequestMapping("/api/admin/prices/daily")
public class AdminDailyPriceController {

  @PersistenceContext
  private EntityManager em;

  private static final List<String> LEVELS = List.of("TBY", "TB", "TB+");

  public record UpdateRequest(
    @NotNull @Positive Long fixedFee,
    @NotNull @Positive Long productId
  ) {}

  public record ProductRow(Long id, String name, String imageUrl, boolean active) {}

  public record LevelRow(String skillLevel, Long fixedFee, ProductRow product) {}

  public record Configuration(List<LevelRow> levels, List<ProductRow> products) {}

  private ProductRow product(Product p) {
    return p == null
      ? null
      : new ProductRow(p.getId(), p.getName(), p.getImageUrl(), Boolean.TRUE.equals(p.getActive()));
  }

  private LevelRow row(String level) {
    var cfg = em.find(DailyVisitorLevelConfig.class, level);
    return new LevelRow(
      level,
      cfg == null ? 80000L : cfg.getFixedFee(),
      product(cfg == null ? em.find(Product.class, 1L) : cfg.getShuttlecockProduct())
    );
  }

  @GetMapping("/levels")
  @Transactional(readOnly = true)
  public Configuration get() {
    return new Configuration(
      LEVELS.stream().map(this::row).toList(),
      em
        .createQuery("select p from Product p order by p.name", Product.class)
        .getResultList()
        .stream()
        .map(this::product)
        .toList()
    );
  }

  @PutMapping("/levels/{level}")
  @Transactional
  public LevelRow update(@PathVariable String level, @Valid @RequestBody UpdateRequest body) {
    level = level.trim().toUpperCase(Locale.ROOT);
    if (!LEVELS.contains(level)) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "Chỉ hỗ trợ TBY, TB và TB+."
    );
    var p = em.find(Product.class, body.productId());
    if (p == null || !Boolean.TRUE.equals(p.getActive())) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "Chọn loại ống cầu đang hoạt động."
    );
    var cfg = em.find(DailyVisitorLevelConfig.class, level, LockModeType.PESSIMISTIC_WRITE);
    if (cfg == null) {
      cfg = new DailyVisitorLevelConfig();
      cfg.setSkillLevel(level);
      em.persist(cfg);
    }
    cfg.setFixedFee(body.fixedFee());
    cfg.setShuttlecockProduct(p);
    // Keep the public schedule catalogue consistent with the level price.
    em.createQuery(
      "update DailyVisitorSchedule d set d.fixedFee = :fee where upper(trim(d.skillLevel)) = :level"
    )
      .setParameter("fee", body.fixedFee())
      .setParameter("level", level)
      .executeUpdate();
    // Sessions already created retain their fee/product snapshots.
    return new LevelRow(level, cfg.getFixedFee(), product(p));
  }
}
