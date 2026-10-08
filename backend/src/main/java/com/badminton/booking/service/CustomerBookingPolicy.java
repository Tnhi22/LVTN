package com.badminton.booking.service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CustomerBookingPolicy {

  private final String pieceCourtTypes;
  private final int maxAdvanceDays;

  public CustomerBookingPolicy(
    @Value("${app.booking.piece-court-types:*}") String pieceCourtTypes,
    @Value("${app.booking.max-advance-days:-1}") int maxAdvanceDays
  ) {
    this.pieceCourtTypes = pieceCourtTypes;
    this.maxAdvanceDays = maxAdvanceDays;
  }

  public LocalDate lastBookingDate(LocalDate today) {
    if (maxAdvanceDays >= 0) return today.plusDays(maxAdvanceDays);
    LocalDate sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    return today.getDayOfWeek() == DayOfWeek.MONDAY ? sunday : sunday.plusWeeks(1);
  }

  public boolean allowsPieces(String type) {
    return (
      "*".equals(pieceCourtTypes.trim()) ||
      Arrays.stream(pieceCourtTypes.split(",")).anyMatch(value ->
        value.trim().equalsIgnoreCase(type)
      )
    );
  }
}
