package com.badminton.booking.entity;

import jakarta.persistence.*;

/** One shared price and shuttlecock configuration per Daily skill level. */
@Entity
@Table(name = "daily_visitor_level_configs")
public class DailyVisitorLevelConfig {

  @Id
  @Column(name = "skill_level", length = 10)
  private String skillLevel;

  @Column(name = "fixed_fee", nullable = false)
  private Long fixedFee = 80000L;

  @ManyToOne
  @JoinColumn(name = "shuttlecock_product_id")
  private Product shuttlecockProduct;

  public DailyVisitorLevelConfig() {}

  public String getSkillLevel() {
    return skillLevel;
  }

  public void setSkillLevel(String skillLevel) {
    this.skillLevel = skillLevel;
  }

  public Long getFixedFee() {
    return fixedFee;
  }

  public void setFixedFee(Long fixedFee) {
    this.fixedFee = fixedFee;
  }

  public Product getShuttlecockProduct() {
    return shuttlecockProduct;
  }

  public void setShuttlecockProduct(Product product) {
    this.shuttlecockProduct = product;
  }
}
