package com.badminton.booking.service;

import com.badminton.booking.entity.DailyVisitorParticipant;
import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.entity.User;
import com.badminton.booking.repository.DailyVisitorParticipantRepository;
import com.badminton.booking.repository.DailyVisitorSessionRepository;
import com.badminton.booking.repository.UserRepository;
import org.springframework.stereotype.Service;
import com.badminton.booking.entity.Visitor;
import com.badminton.booking.repository.VisitorRepository;
import java.time.LocalDateTime;

@Service
public class DailyVisitorParticipantService {

    private final DailyVisitorParticipantRepository participantRepository;
    private final DailyVisitorSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final VisitorRepository visitorRepository;


        public DailyVisitorParticipantService(
                DailyVisitorParticipantRepository participantRepository,
                DailyVisitorSessionRepository sessionRepository,
                UserRepository userRepository,
                VisitorRepository visitorRepository) {

        this.participantRepository = participantRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.visitorRepository = visitorRepository;
        }
    // =========================================================
    // 1. CUSTOMER đăng ký online
    // 1 tài khoản = 1 slot / session
    // =========================================================
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

        // Chỉ CUSTOMER tự đăng ký
        if (!"CUSTOMER".equals(user.getRole())) {
            throw new RuntimeException(
                    "Chỉ khách hàng mới có thể tự đăng ký lượt chơi");
        }

        if (!"OPEN".equals(session.getStatus())) {
            throw new RuntimeException(
                    "Lượt chơi đã đầy hoặc không còn mở đăng ký");
        }

        // Không cho cùng user đăng ký 2 lần cùng session
        boolean alreadyRegistered =
                participantRepository.existsBySessionIdAndUserId(
                        sessionId,
                        userId
                );

        if (alreadyRegistered) {
            throw new RuntimeException(
                    "Bạn đã đăng ký lượt chơi này");
        }

        // Tổng số slot đã sử dụng
        Long usedSlots =
                participantRepository.getUsedSlots(sessionId);

        if (usedSlots == null) {
            usedSlots = 0L;
        }

        long remainingSlots =
                session.getMaxParticipants() - usedSlots;

        if (remainingSlots < 1) {
            session.setStatus("FULL");
            sessionRepository.save(session);

            throw new RuntimeException(
                    "Lượt chơi đã đủ số người");
        }

        // CUSTOMER online luôn chiếm 1 slot
        DailyVisitorParticipant participant =
                new DailyVisitorParticipant();

        participant.setSession(session);
        participant.setUser(user);
        participant.setSlotCount(1);
        participant.setStatus("CONFIRMED");

        DailyVisitorParticipant savedParticipant =
                participantRepository.save(participant);

        // Sau khi đăng ký nếu đủ capacity thì chuyển FULL
        if (usedSlots + 1 >= session.getMaxParticipants()) {
            session.setStatus("FULL");
            sessionRepository.save(session);
        }

        return savedParticipant;
    }


    // =========================================================
    // 2. STAFF đăng ký khách tại quầy
    // Chỉ cần SĐT người đại diện + số slot
    // =========================================================
    public DailyVisitorParticipant registerWalkIn(
        Long sessionId,
        String fullName,
        String phone,
        Integer slotCount) {

    DailyVisitorSession session =
            sessionRepository.findById(sessionId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Không tìm thấy lượt chơi"));

    if (!"OPEN".equals(session.getStatus())) {
        throw new RuntimeException(
                "Lượt chơi đã đầy hoặc không còn mở đăng ký");
    }

    // Kiểm tra tên khách
    if (fullName == null || fullName.trim().isEmpty()) {
        throw new RuntimeException(
                "Vui lòng nhập tên khách hàng");
    }

    // Kiểm tra SĐT
    if (phone == null || phone.trim().isEmpty()) {
        throw new RuntimeException(
                "Vui lòng nhập số điện thoại người đại diện");
    }
    String normalizedPhone = phone.trim();
        String normalizedName = fullName.trim();

        Visitor visitor = visitorRepository.findByPhone(normalizedPhone)
                .orElseGet(() -> {
                Visitor newVisitor = new Visitor();
                newVisitor.setFullName(normalizedName);
                newVisitor.setPhone(normalizedPhone);
                newVisitor.setStatus("ACTIVE");
                return visitorRepository.save(newVisitor);
                });

        if ("BLOCKED".equals(visitor.getStatus())) {
        throw new RuntimeException(
                "Số điện thoại này đã bị khóa do từng không đến nhận sân"
        );
        }

    // Kiểm tra số slot
    if (slotCount == null || slotCount <= 0) {
        throw new RuntimeException(
                "Số slot đăng ký phải lớn hơn 0");
    }

    Long usedSlots =
            participantRepository.getUsedSlots(sessionId);

    if (usedSlots == null) {
        usedSlots = 0L;
    }

    long remainingSlots =
            session.getMaxParticipants() - usedSlots;

    if (remainingSlots <= 0) {
        session.setStatus("FULL");
        sessionRepository.save(session);

        throw new RuntimeException(
                "Lượt chơi đã đủ số người");
    }

    if (slotCount > remainingSlots) {
        throw new RuntimeException(
                "Lượt chơi chỉ còn "
                        + remainingSlots
                        + " slot trống");
    }

    DailyVisitorParticipant participant =
            new DailyVisitorParticipant();

    participant.setSession(session);

    // Khách tại quầy không có tài khoản User
    participant.setUser(null);

    // Lưu tên + SĐT
    participant.setParticipantName(normalizedName);
    participant.setRepresentativePhone(normalizedPhone);

    participant.setSlotCount(slotCount);
    participant.setStatus("CONFIRMED");

    DailyVisitorParticipant savedParticipant =
            participantRepository.save(participant);

    if (usedSlots + slotCount
            >= session.getMaxParticipants()) {

        session.setStatus("FULL");
        sessionRepository.save(session);
    }

    return savedParticipant;
}

// =========================================================
// 3. STAFF / ADMIN check-in Daily Visitor
// =========================================================
public DailyVisitorParticipant checkIn(
        Long participantId,
        Long staffId) {

    DailyVisitorParticipant participant =
            participantRepository.findById(participantId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Không tìm thấy người đăng ký"));

    User staff =
            userRepository.findById(staffId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Không tìm thấy nhân viên"));

    // Chỉ STAFF / ADMIN được check-in
    if (!"STAFF".equals(staff.getRole())
            && !"ADMIN".equals(staff.getRole())) {

        throw new RuntimeException(
                "Chỉ STAFF hoặc ADMIN mới có thể check-in");
    }

    // Chỉ participant đang CONFIRMED mới được check-in
    if (!"CONFIRMED".equals(participant.getStatus())) {

        if ("CHECKED_IN".equals(participant.getStatus())) {
            throw new RuntimeException(
                    "Người chơi đã được check-in trước đó");
        }

        if ("NO_SHOW".equals(participant.getStatus())) {
            throw new RuntimeException(
                    "Người chơi đã bị đánh dấu NO_SHOW");
        }

        throw new RuntimeException(
                "Trạng thái hiện tại không cho phép check-in");
    }

    DailyVisitorSession session =
            participant.getSession();

    if (session == null) {
        throw new RuntimeException(
                "Không tìm thấy lượt chơi");
    }

    // Session đã hủy / đóng thì không được check-in
    if ("CANCELLED".equals(session.getStatus())
            || "CLOSED".equals(session.getStatus())) {

        throw new RuntimeException(
                "Lượt chơi đã bị hủy hoặc đã đóng");
    }

    LocalDateTime now = LocalDateTime.now();

    LocalDateTime sessionStart =
            LocalDateTime.of(
                    session.getSessionDate(),
                    session.getStartTime()
            );

    LocalDateTime finalCheckInTime =
            sessionStart.plusMinutes(30);

    // Không cho check-in trước giờ chơi
    if (now.isBefore(sessionStart)) {
        throw new RuntimeException(
                "Chưa đến thời gian check-in");
    }

    // Quá 30 phút thì không cho check-in
    if (!now.isBefore(finalCheckInTime)) {
        throw new RuntimeException(
                "Đã quá thời gian check-in 30 phút");
    }
    // Check-in nhóm nghĩa là toàn bộ số slot đã đăng ký đều có mặt.
    participant.setCheckedInSlots(participant.getSlotCount());
    participant.setStatus("CHECKED_IN");
    participant.setCheckedInAt(now);
    participant.setCheckedInBy(staff.getId());

    return participantRepository.save(participant);
}
}