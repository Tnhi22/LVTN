package com.badminton.booking.service;

import com.badminton.booking.entity.DailyVisitorLevelConfig;
import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One fee per person, independent of the session's duration or headcount. */
@Service
public class DailyVisitorFeePolicy {

  @PersistenceContext
  private EntityManager em;

  @Transactional(readOnly = true)
  public long feePerPerson(DailyVisitorSession session) {
    String level = session.getSchedule().getSkillLevel().trim().toUpperCase(Locale.ROOT);
    DailyVisitorLevelConfig config = em.find(DailyVisitorLevelConfig.class, level);
    if (config == null || config.getFixedFee() == null || config.getFixedFee() <= 0) {
      throw new BusinessException(
        HttpStatus.CONFLICT,
        "Chưa cấu hình giá mỗi người cho Daily " + level
      );
    }
    return config.getFixedFee();
  }
}
