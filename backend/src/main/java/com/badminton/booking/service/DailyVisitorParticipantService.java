package com.badminton.booking.service;

import com.badminton.booking.entity.DailyVisitorParticipant;
import com.badminton.booking.entity.DailyVisitorSession;
import com.badminton.booking.entity.User;
import com.badminton.booking.entity.Visitor;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.DailyVisitorParticipantRepository;
import com.badminton.booking.repository.DailyVisitorSessionRepository;
import com.badminton.booking.repository.UserRepository;
import com.badminton.booking.repository.VisitorRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class DailyVisitorParticipantService {

    private final DailyVisitorParticipantRepository participantRepository;
    private final DailyVisitorSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final VisitorRepository visitorRepository;
    private final DailyVisitorInventoryService
        dailyVisitorInventoryService;
    

        public DailyVisitorParticipantService(
                DailyVisitorParticipantRepository participantRepository,
                DailyVisitorSessionRepository sessionRepository,
                UserRepository userRepository,
                VisitorRepository visitorRepository,
                DailyVisitorInventoryService
                        dailyVisitorInventoryService) {

        this.participantRepository = participantRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.visitorRepository = visitorRepository;
        this.dailyVisitorInventoryService =
                dailyVisitorInventoryService;
        }

    // =========================================================
    // 1. CUSTOMER đăng ký online
    // 1 tài khoản = 1 slot/session
    // =========================================================
    @Transactional
    public DailyVisitorParticipant register(
            Long sessionId,
            Long userId) {

        if (sessionId == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn lượt chơi"
            );
        }

        if (userId == null) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Không xác định được người dùng đăng nhập"
            );
        }

        DailyVisitorSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy lượt chơi"
                ));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy người dùng"
                ));

        if (!"CUSTOMER".equals(user.getRole())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ khách hàng mới có thể tự đăng ký lượt chơi"
            );
        }

        if ("SUSPENDED".equals(user.getStatus())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Tài khoản đã bị khóa nên không thể đăng ký lượt chơi"
            );
        }

        if (!"OPEN".equals(session.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Lượt chơi đã đầy hoặc không còn mở đăng ký"
            );
        }

        boolean alreadyRegistered =
                participantRepository.existsBySessionIdAndUserId(
                        sessionId,
                        userId
                );

        if (alreadyRegistered) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Bạn đã đăng ký lượt chơi này"
            );
        }

        Long usedSlots = participantRepository.getUsedSlots(sessionId);
        if (usedSlots == null) {
            usedSlots = 0L;
        }

        long remainingSlots = session.getMaxParticipants() - usedSlots;

        if (remainingSlots < 1) {
            session.setStatus("FULL");
            sessionRepository.save(session);

            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Lượt chơi đã đủ số người"
            );
        }

        DailyVisitorParticipant participant =
                new DailyVisitorParticipant();

        participant.setSession(session);
        participant.setUser(user);
        participant.setSlotCount(1);
        participant.setStatus("CONFIRMED");

        DailyVisitorParticipant savedParticipant =
                participantRepository.save(participant);

        if (usedSlots + 1 >= session.getMaxParticipants()) {
            session.setStatus("FULL");
            sessionRepository.save(session);
        }

        return savedParticipant;
    }

    // =========================================================
    // 2. STAFF đăng ký khách tại quầy
    // =========================================================
    @Transactional
    public DailyVisitorParticipant registerWalkIn(
            Long sessionId,
            String fullName,
            String phone,
            Integer slotCount) {

        if (sessionId == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn lượt chơi"
            );
        }

        DailyVisitorSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy lượt chơi"
                ));

        if (!"OPEN".equals(session.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Lượt chơi đã đầy hoặc không còn mở đăng ký"
            );
        }

        if (fullName == null || fullName.trim().isEmpty()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập tên khách hàng"
            );
        }

        if (phone == null || phone.trim().isEmpty()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập số điện thoại người đại diện"
            );
        }

        if (slotCount == null || slotCount <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Số slot đăng ký phải lớn hơn 0"
            );
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
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Số điện thoại này đã bị khóa do từng không đến nhận sân"
            );
        }

        Long usedSlots = participantRepository.getUsedSlots(sessionId);
        if (usedSlots == null) {
            usedSlots = 0L;
        }

        long remainingSlots = session.getMaxParticipants() - usedSlots;

        if (remainingSlots <= 0) {
            session.setStatus("FULL");
            sessionRepository.save(session);

            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Lượt chơi đã đủ số người"
            );
        }

        if (slotCount > remainingSlots) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Lượt chơi chỉ còn " + remainingSlots + " slot trống"
            );
        }

        DailyVisitorParticipant participant =
                new DailyVisitorParticipant();

        participant.setSession(session);
        participant.setUser(null);
        participant.setParticipantName(normalizedName);
        participant.setRepresentativePhone(normalizedPhone);
        participant.setSlotCount(slotCount);
        participant.setStatus("CONFIRMED");

        DailyVisitorParticipant savedParticipant =
                participantRepository.save(participant);

        if (usedSlots + slotCount >= session.getMaxParticipants()) {
            session.setStatus("FULL");
            sessionRepository.save(session);
        }

        return savedParticipant;
    }

    // =========================================================
    // 3. STAFF/ADMIN check-in Daily Visitor
    // =========================================================
    @Transactional
    public DailyVisitorParticipant checkIn(
            Long participantId,
            Long staffId) {

        DailyVisitorParticipant participant =
                participantRepository.findById(participantId)
                        .orElseThrow(() -> new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy người đăng ký"
                        ));

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhân viên"
                ));

        if (!"STAFF".equals(staff.getRole())
                && !"ADMIN".equals(staff.getRole())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ STAFF hoặc ADMIN mới có thể check-in"
            );
        }

        if (!"CONFIRMED".equals(participant.getStatus())) {
            if ("CHECKED_IN".equals(participant.getStatus())) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Người chơi đã được check-in trước đó"
                );
            }

            if ("NO_SHOW".equals(participant.getStatus())) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Người chơi đã bị đánh dấu NO_SHOW"
                );
            }

            if ("CANCELLED".equals(participant.getStatus())) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "Lượt đăng ký đã bị hủy"
                );
            }

            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Trạng thái hiện tại không cho phép check-in"
            );
        }

        DailyVisitorSession session = participant.getSession();
        if (session == null) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy lượt chơi"
            );
        }

        if ("CANCELLED".equals(session.getStatus())
                || "CLOSED".equals(session.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Lượt chơi đã bị hủy hoặc đã đóng"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sessionStart = LocalDateTime.of(
                session.getSessionDate(),
                session.getStartTime()
        );
        LocalDateTime finalCheckInTime = sessionStart.plusMinutes(30);

        if (now.isBefore(sessionStart)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Chưa đến thời gian check-in"
            );
        }

        if (!now.isBefore(finalCheckInTime)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Đã quá thời gian check-in 30 phút"
            );
        }

        participant.setCheckedInSlots(
                participant.getSlotCount()
        );
        participant.setStatus("CHECKED_IN");
        participant.setCheckedInAt(now);
        participant.setCheckedInBy(staff.getId());

        DailyVisitorParticipant savedParticipant =
                participantRepository.saveAndFlush(participant);

        Long checkedInSlots =
                participantRepository.getCheckedInSlots(
                        session.getId()
                );

        if (checkedInSlots == null) {
        checkedInSlots = 0L;
        }

        // Khi đủ số người tối thiểu thì xuất đúng 1 ống cầu FIFO
        if (checkedInSlots >= session.getMinParticipants()
                && !Boolean.TRUE.equals(
                        session.getShuttlecockIssued())) {

        dailyVisitorInventoryService
                .issueForSession(session);
        }

        return savedParticipant;
    }

    // =========================================================
    // 4. CUSTOMER tự hủy Daily Visitor
    // =========================================================
    @Transactional
    public DailyVisitorParticipant cancelByCustomer(
            Long participantId,
            Long userId) {

        DailyVisitorParticipant participant =
                participantRepository.findById(participantId)
                        .orElseThrow(() -> new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy lượt đăng ký"
                        ));

        if (participant.getUser() == null) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Khách tại quầy không thể tự hủy trên web"
            );
        }

        if (!participant.getUser().getId().equals(userId)) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền hủy lượt đăng ký này"
            );
        }

        if (!"CUSTOMER".equals(participant.getUser().getRole())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ khách hàng mới có thể tự hủy lượt đăng ký"
            );
        }

        validateParticipantCanBeCancelled(participant);

        DailyVisitorSession session = participant.getSession();
        validateSessionCanBeCancelled(session);
        validateCancellationDeadline(session);

        participant.setStatus("CANCELLED");
        DailyVisitorParticipant savedParticipant =
                participantRepository.save(participant);

        reopenSessionIfFull(session);
        return savedParticipant;
    }

    // =========================================================
    // 5. STAFF/ADMIN hủy Daily Visitor cho khách tại quầy
    // =========================================================
    @Transactional
    public DailyVisitorParticipant cancelWalkInByStaff(
            Long participantId,
            Long staffId) {

        DailyVisitorParticipant participant =
                participantRepository.findById(participantId)
                        .orElseThrow(() -> new BusinessException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy lượt đăng ký"
                        ));

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhân viên"
                ));

        if (!"STAFF".equals(staff.getRole())
                && !"ADMIN".equals(staff.getRole())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ STAFF hoặc ADMIN mới có thể hủy cho khách tại quầy"
            );
        }

        if (participant.getUser() != null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Đây là khách có tài khoản, khách phải tự hủy trên web"
            );
        }

        validateParticipantCanBeCancelled(participant);

        DailyVisitorSession session = participant.getSession();
        validateSessionCanBeCancelled(session);
        validateCancellationDeadline(session);

        participant.setStatus("CANCELLED");
        DailyVisitorParticipant savedParticipant =
                participantRepository.save(participant);

        reopenSessionIfFull(session);
        return savedParticipant;
    }

    private void validateParticipantCanBeCancelled(
            DailyVisitorParticipant participant) {

        if ("CONFIRMED".equals(participant.getStatus())) {
            return;
        }

        if ("CANCELLED".equals(participant.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Lượt đăng ký đã được hủy trước đó"
            );
        }

        if ("CHECKED_IN".equals(participant.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Lượt đăng ký đã check-in nên không thể hủy"
            );
        }

        if ("NO_SHOW".equals(participant.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Lượt đăng ký đã bị đánh dấu NO_SHOW"
            );
        }

        throw new BusinessException(
                HttpStatus.CONFLICT,
                "Trạng thái hiện tại không cho phép hủy"
        );
    }

    private void validateSessionCanBeCancelled(
            DailyVisitorSession session) {

        if (session == null) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy lượt chơi"
            );
        }

        if ("CANCELLED".equals(session.getStatus())
                || "CLOSED".equals(session.getStatus())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "Lượt chơi đã bị hủy hoặc đã đóng"
            );
        }
    }

    private void validateCancellationDeadline(
            DailyVisitorSession session) {

        LocalDateTime sessionStart = LocalDateTime.of(
                session.getSessionDate(),
                session.getStartTime()
        );
        LocalDateTime cancelDeadline = sessionStart.minusMinutes(30);

        if (!LocalDateTime.now().isBefore(cancelDeadline)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ được hủy trước giờ chơi ít nhất 30 phút"
            );
        }
    }

    private void reopenSessionIfFull(
            DailyVisitorSession session) {

        if ("FULL".equals(session.getStatus())) {
            session.setStatus("OPEN");
            sessionRepository.save(session);
        }
    }
}
