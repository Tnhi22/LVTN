package com.badminton.booking.controller;

import com.badminton.booking.entity.Court;
import com.badminton.booking.entity.Room;
import com.badminton.booking.entity.CourtType;
import com.badminton.booking.repository.CourtTypeRepository;
import com.badminton.booking.entity.DailyVisitorSchedule;
import com.badminton.booking.repository.DailyVisitorScheduleRepository;
import com.badminton.booking.service.CourtRoomPolicy;
import com.badminton.booking.service.DailyVisitorSessionService;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import com.badminton.booking.exception.BusinessException;
import com.badminton.booking.repository.CourtRepository;
import com.badminton.booking.repository.RoomRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/courts")
public class AdminCourtController {
    private final CourtRepository courts;
    private final RoomRepository rooms;
    private final EntityManager entityManager;
    private final CourtTypeRepository types;
    private final DailyVisitorScheduleRepository schedules;
    private final DailyVisitorSessionService sessions;
    public AdminCourtController(CourtRepository courts, RoomRepository rooms, EntityManager entityManager,
                                DailyVisitorScheduleRepository schedules, DailyVisitorSessionService sessions, CourtTypeRepository types) {
        this.courts = courts; this.rooms = rooms; this.entityManager = entityManager;
        this.schedules = schedules; this.sessions = sessions; this.types = types;
    }
    public record DailyRequest(@NotBlank String skillLevel, @NotNull LocalTime startTime,
                               @NotNull LocalTime endTime, @NotNull @Positive Long fixedFee,
                               @NotNull @Min(1) Integer minParticipants,
                               @NotNull @Min(1) Integer maxParticipants) {}
    public record DailyResponse(Long id, String skillLevel, LocalTime startTime, LocalTime endTime,
                                Long fixedFee, Integer minParticipants, Integer maxParticipants, Boolean active) {}
    public record NewRoomRequest(@NotBlank @Size(max = 255) String name,
                                 @NotNull @Positive Long courtTypeId, @NotBlank String roomGroup) {}
    public record TypeResponse(Long id, String name, Boolean active) {}
    public record CourtRequest(@NotBlank @Size(max = 255) String name,
                               @Positive Long roomId,
                               @NotNull Boolean active,
                               @Min(1) @Max(120) Integer maintenanceIntervalMonths,
                               LocalDateTime nextMaintenanceAt, @Valid DailyRequest dailySchedule,
                               @Valid NewRoomRequest newRoom, String roomGroup) {}
    public record ActiveRequest(@NotNull Boolean active) {}
    public record CourtResponse(Long id, String name, Long roomId, String roomName,
                                String courtTypeName, Boolean active,
                                Integer maintenanceIntervalMonths, LocalDateTime nextMaintenanceAt,
                                String roomGroup, List<DailyResponse> dailySchedules) {}
    public record RoomResponse(Long id, String name, Long courtTypeId, String courtTypeName,
                               Boolean active, String roomGroup, int capacity, long courtCount) {}
    @GetMapping
    @Transactional(readOnly = true)
    public List<CourtResponse> list() { return courts.findAll().stream().map(this::response).toList(); }
    @GetMapping("/rooms")
    @Transactional(readOnly = true)
    public List<RoomResponse> roomList() {
        return rooms.findAll().stream().map(r -> roomResponse(r)).toList();
    }
    @GetMapping("/types")
    @Transactional(readOnly = true)
    public List<TypeResponse> typeList() {
        return types.findAll().stream().map(t -> new TypeResponse(t.getId(), t.getName(), t.getActive())).toList();
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CourtResponse create(@Valid @RequestBody CourtRequest request) { return save(new Court(), request); }
    @PutMapping("/{id}")
    @Transactional
    public CourtResponse update(@PathVariable Long id, @Valid @RequestBody CourtRequest request) { return save(find(id), request); }
    @PatchMapping("/{id}/active")
    @Transactional
    public CourtResponse active(@PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
        Court court = find(id); court.setActive(request.active()); return response(courts.saveAndFlush(court));
    }
    @DeleteMapping("/{id}")
    @Transactional
    public java.util.Map<String, String> delete(@PathVariable Long id) {
        Court court = find(id);
        for (String entity : List.of("NormalBooking", "DailyVisitorSchedule", "CourtMaintenance", "CourtReview")) {
            Long count = entityManager.createQuery("SELECT COUNT(e) FROM " + entity + " e WHERE e.court.id = :id", Long.class)
                    .setParameter("id", id).getSingleResult();
            if (count > 0) throw new BusinessException(HttpStatus.CONFLICT,
                    "Sân đã có lịch đặt hoặc dữ liệu liên quan. Hãy tắt sân thay vì xóa.");
        }
        try {
            courts.delete(court);
            courts.flush();
        } catch (DataIntegrityViolationException error) {
            throw new BusinessException(HttpStatus.CONFLICT, "Sân có dữ liệu liên quan, không thể xóa. Hãy tắt sân.");
        }
        return java.util.Map.of("message", "Đã xóa sân");
    }
    private Court find(Long id) {
        return courts.findByIdForBooking(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy sân"));
    }
    private CourtResponse save(Court court, CourtRequest request) {
        String name = request.name().trim();
        if (name.isBlank()) throw new BusinessException(HttpStatus.BAD_REQUEST, "Tên sân không được để trống");
        Long duplicate = entityManager.createQuery("SELECT COUNT(c) FROM Court c WHERE LOWER(c.name) = LOWER(:name) AND c.id <> :id", Long.class)
                .setParameter("name", name).setParameter("id", court.getId() == null ? -1L : court.getId()).getSingleResult();
        if (duplicate > 0) throw new BusinessException(HttpStatus.CONFLICT, "Tên sân đã tồn tại");
        boolean creating = court.getId() == null;
        Room room = resolveRoom(request, creating);
        String group = group(room);
        if (creating && "UNCONFIGURED".equals(group)) {
            if (request.roomGroup() == null || !CourtRoomPolicy.accepts(room.getCourtType().getName(), request.roomGroup()))
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Chọn cấu trúc phòng phù hợp với loại sân");
            room.setRoomGroup(request.roomGroup());
            group = request.roomGroup();
        }
        if (creating) {
            if ("PREMIUM_PRIVATE".equals(group) && courtCount(room.getId()) >= 1)
                throw new BusinessException(HttpStatus.CONFLICT,
                        "Phòng Private chỉ có một sân. Hãy chọn tạo phòng Private mới.");
            if (!Boolean.TRUE.equals(room.getCourtType().getActive()))
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Loại sân đang ngừng hoạt động");
            if ("DAILY_VISITOR".equals(group)) {
                validateDaily(request.dailySchedule());

            }
            else if (request.dailySchedule() != null) throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Chỉ sân Mist_fan/DailyVisitor được kèm lịch vãng lai");
        } else if (request.dailySchedule() != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Sửa sân không thay đổi lịch vãng lai đã có");
        }
        if (court.getId() == null && !Boolean.TRUE.equals(room.getActive()))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Không thể thêm sân vào phòng ngừng hoạt động");
        if (court.getId() != null && !court.getRoom().getId().equals(room.getId()))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Không thể chuyển phòng của sân đã tạo");
        court.setName(name); court.setRoom(room); court.setActive(request.active());
        court.setMaintenanceIntervalMonths(request.maintenanceIntervalMonths());
        court.setNextMaintenanceAt(request.nextMaintenanceAt());
        try {
            courts.saveAndFlush(court);
            if (creating && "DAILY_VISITOR".equals(group)) {
                DailyRequest d = request.dailySchedule();
                DailyVisitorSchedule schedule = new DailyVisitorSchedule();
                schedule.setCourt(court); schedule.setSkillLevel(d.skillLevel());
                schedule.setStartTime(d.startTime()); schedule.setEndTime(d.endTime());
                schedule.setFixedFee(d.fixedFee()); schedule.setMinParticipants(d.minParticipants());
                schedule.setMaxParticipants(d.maxParticipants()); schedule.setActive(true);
                schedules.saveAndFlush(schedule);
                for (int day = 0; day < 3; day++) sessions.generateSessionsForSchedule(LocalDate.now().plusDays(day), schedule.getId());
            }
            return response(court);
        }
        catch (DataIntegrityViolationException error) { throw new BusinessException(HttpStatus.CONFLICT, "Không thể lưu sân. Kiểm tra tên sân trùng hoặc dữ liệu liên kết."); }
    }
    private String group(Room room) {
        return room.getRoomGroup() == null || room.getRoomGroup().isBlank()
                ? CourtRoomPolicy.group(room.getCourtType().getName(), room.getName()) : room.getRoomGroup();
    }
    private Room resolveRoom(CourtRequest request, boolean creating) {
        if ((request.roomId() == null) == (request.newRoom() == null))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Chọn phòng có sẵn hoặc tạo phòng mới, không chọn cả hai");
        if (request.newRoom() != null) {
            if (!creating) throw new BusinessException(HttpStatus.BAD_REQUEST, "Không tạo phòng mới khi sửa sân");
            NewRoomRequest n = request.newRoom();
            if (!"PREMIUM_PRIVATE".equals(n.roomGroup()))
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Form này chỉ tạo phòng mới cho Premium Private");
            // Serialize private room creates for the same type, including room name validation.
            CourtType type = java.util.Optional.ofNullable(entityManager.find(CourtType.class, n.courtTypeId(), LockModeType.PESSIMISTIC_WRITE))
                    .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "Loại sân không tồn tại"));
            if (!Boolean.TRUE.equals(type.getActive()) || !CourtRoomPolicy.accepts(type.getName(), n.roomGroup()))
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Loại sân không phù hợp cho phòng Private");
            String name = n.name().trim();
            if (name.isBlank()) throw new BusinessException(HttpStatus.BAD_REQUEST, "Tên phòng không được trống");
            long duplicate = entityManager.createQuery("SELECT COUNT(r) FROM Room r WHERE LOWER(r.name) = LOWER(:name)", Long.class)
                    .setParameter("name", name).getSingleResult();
            if (duplicate > 0) throw new BusinessException(HttpStatus.CONFLICT, "Tên phòng đã tồn tại");
            Room room = new Room(); room.setName(name); room.setCourtType(type);
            room.setRoomGroup(n.roomGroup()); room.setActive(true);
            return rooms.saveAndFlush(room);
        }
        return java.util.Optional.ofNullable(entityManager.find(Room.class, request.roomId(), LockModeType.PESSIMISTIC_WRITE))
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "Phòng sân không tồn tại"));
    }
    private long courtCount(Long roomId) {
        return entityManager.createQuery("SELECT COUNT(c) FROM Court c WHERE c.room.id = :id", Long.class)
                .setParameter("id", roomId).getSingleResult();
    }
    private RoomResponse roomResponse(Room room) {
        String group = group(room);
        return new RoomResponse(room.getId(), room.getName(), room.getCourtType().getId(),
                room.getCourtType().getName(), Boolean.TRUE.equals(room.getActive()) && Boolean.TRUE.equals(room.getCourtType().getActive()),
                group, CourtRoomPolicy.capacity(group), courtCount(room.getId()));
    }
    private void validateDaily(DailyRequest d) {
        if (d == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "Sân vãng lai phải có trình độ và lịch cố định");
        if (!List.of("TBY", "TB", "TB+").contains(d.skillLevel()))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Trình độ phải là TBY, TB hoặc TB+");
        if (!d.endTime().isAfter(d.startTime()) || d.minParticipants() > d.maxParticipants())
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Giờ hoặc giới hạn người chơi không hợp lệ");
    }
    private CourtResponse response(Court c) {
        return new CourtResponse(c.getId(), c.getName(), c.getRoom().getId(), c.getRoom().getName(),
                c.getRoom().getCourtType().getName(), c.getActive(), c.getMaintenanceIntervalMonths(), c.getNextMaintenanceAt(),
                group(c.getRoom()),
                schedules.findAll().stream().filter(d -> d.getCourt().getId().equals(c.getId()))
                        .map(d -> new DailyResponse(d.getId(), d.getSkillLevel(), d.getStartTime(), d.getEndTime(),
                                d.getFixedFee(), d.getMinParticipants(), d.getMaxParticipants(), d.getActive())).toList());
    }
}
