package com.badminton.booking.service;

import com.badminton.booking.entity.DailyVisitorParticipant;
import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.entity.User;
import com.badminton.booking.repository.DailyVisitorParticipantRepository;
import com.badminton.booking.repository.DailyVisitorSessionRepository;
import com.badminton.booking.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class DailyVisitorParticipantService {

    private final DailyVisitorParticipantRepository participantRepository;
    private final DailyVisitorSessionRepository sessionRepository;
    private final UserRepository userRepository;

    public DailyVisitorParticipantService(
            DailyVisitorParticipantRepository participantRepository,
            DailyVisitorSessionRepository sessionRepository,
            UserRepository userRepository) {

        this.participantRepository = participantRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    public DailyVisitorParticipant register(
            Long sessionId,
            Long userId) {

        DailyVisitorSession session =
                sessionRepository.findById(sessionId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Không tìm thấy lượt chơi"));

        User user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Không tìm thấy người dùng"));

        if (!"OPEN".equals(session.getStatus())) {
            throw new RuntimeException(
                    "Lượt chơi đã đầy hoặc không còn mở đăng ký");
        }

        boolean alreadyRegistered =
                participantRepository.existsBySessionIdAndUserId(
                        sessionId,
                        userId
                );

        if (alreadyRegistered) {
            throw new RuntimeException(
                    "Bạn đã đăng ký lượt chơi này");
        }

        long currentParticipants =
                participantRepository.countBySessionIdAndStatus(
                        sessionId,
                        "CONFIRMED"
                );

        if (currentParticipants >= session.getMaxParticipants()) {
            session.setStatus("FULL");
            sessionRepository.save(session);

            throw new RuntimeException(
                    "Lượt chơi đã đủ số người");
        }

        DailyVisitorParticipant participant =
                new DailyVisitorParticipant();

        participant.setSession(session);
        participant.setUser(user);
        participant.setStatus("CONFIRMED");

        DailyVisitorParticipant savedParticipant =
                participantRepository.save(participant);

        long newParticipantCount =
                currentParticipants + 1;

        if (newParticipantCount >= session.getMaxParticipants()) {
            session.setStatus("FULL");
            sessionRepository.save(session);
        }

        return savedParticipant;
    }
}