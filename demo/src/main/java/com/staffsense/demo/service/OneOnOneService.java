package com.staffsense.demo.service;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.OneOnOneMeetingRepository;
import com.staffsense.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Встречи 1:1.
 *
 * <p>Исправлено (было 🔴):
 * <ul>
 *   <li>{@code complete}/{@code cancel} не проверяли участника — любой пользователь
 *       мог завершить или отменить чужую встречу по её ID (IDOR);</li>
 *   <li>{@code create} позволял назначить встречу любому сотруднику и записать
 *       менеджером кого угодно;</li>
 *   <li>{@code missed} никогда не выставлялся — признак для ML был мёртв
 *       (автопометка появится в следующем инкременте, FR-026).</li>
 * </ul>
 */
@Service
public class OneOnOneService {

    private static final Logger log = LoggerFactory.getLogger(OneOnOneService.class);

    private final OneOnOneMeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final TeamAccessService teamAccessService;

    public OneOnOneService(OneOnOneMeetingRepository meetingRepository,
                           UserRepository userRepository,
                           TeamAccessService teamAccessService) {
        this.meetingRepository = meetingRepository;
        this.userRepository = userRepository;
        this.teamAccessService = teamAccessService;
    }

    public List<OneOnOneMeeting> findAllForManager(User manager) {
        return meetingRepository.findAll().stream()
                .filter(m -> m.getManager().getId().equals(manager.getId()))
                .sorted(java.util.Comparator.comparing(OneOnOneMeeting::getScheduledAt).reversed())
                .toList();
    }

    public List<OneOnOneMeeting> findAllForEmployee(User employee) {
        return meetingRepository.findAll().stream()
                .filter(m -> m.getEmployee().getId().equals(employee.getId()))
                .sorted(java.util.Comparator.comparing(OneOnOneMeeting::getScheduledAt).reversed())
                .toList();
    }

    public List<OneOnOneMeeting> findAll() {
        return meetingRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(OneOnOneMeeting::getScheduledAt).reversed())
                .toList();
    }

    public Page<OneOnOneMeeting> findAllForEmployee(User employee, Pageable pageable) {
        return meetingRepository.findByEmployeeId(employee.getId(), pageable);
    }

    public Page<OneOnOneMeeting> findAllForManager(User manager, Pageable pageable) {
        return meetingRepository.findByManagerId(manager.getId(), pageable);
    }

    public Page<OneOnOneMeeting> findAll(Pageable pageable) {
        return meetingRepository.findAll(pageable);
    }

    public OneOnOneMeeting findById(Long id) {
        return meetingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found: " + id));
    }

    /**
     * Назначение встречи. Менеджером записывается фактический руководитель
     * сотрудника (users.manager_id), а если его нет — инициатор.
     * PM может назначать только своим подчинённым, ADMIN/HR — любым.
     */
    @Transactional
    public OneOnOneMeeting create(Long employeeId, User actor, LocalDateTime scheduledAt) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        if (!teamAccessService.canScheduleMeetingWith(actor, employee)) {
            throw new AccessDeniedException("Нет прав назначить встречу сотруднику ID=" + employeeId);
        }

        User manager = employee.getManager() != null ? employee.getManager() : actor;

        OneOnOneMeeting meeting = new OneOnOneMeeting();
        meeting.setEmployee(employee);
        meeting.setManager(manager);
        meeting.setScheduledAt(scheduledAt);
        meeting.setStatus(MeetingStatus.PLANNED);
        meeting.setMissed(false);
        return meetingRepository.save(meeting);
    }

    /** Завершение встречи — только ADMIN или менеджер этой встречи. */
    @Transactional
    public OneOnOneMeeting complete(Long id, String notes, String actionItems, User actor) {
        OneOnOneMeeting m = findById(id);
        teamAccessService.requireCanManageMeeting(actor, m);
        m.setStatus(MeetingStatus.COMPLETED);
        m.setNotes(notes);
        m.setActionItems(actionItems);
        m.setMissed(false);
        return meetingRepository.save(m);
    }

    /** Отмена встречи — только ADMIN или менеджер этой встречи. */
    @Transactional
    public OneOnOneMeeting cancel(Long id, User actor) {
        OneOnOneMeeting m = findById(id);
        teamAccessService.requireCanManageMeeting(actor, m);
        m.setStatus(MeetingStatus.CANCELLED);
        return meetingRepository.save(m);
    }

    /**
     * Автопометка пропущенных встреч (FR-026, BR-021).
     *
     * <p>Встреча в статусе {@code PLANNED}, время которой прошло более суток
     * назад, помечается {@code missed = true}. Это значение используется как
     * признак {@code missed_1on1_ratio} в ML-модели — до реализации этой
     * логики признак всегда был нулевым (кроме сид-данных).
     *
     * @return число обновлённых встреч
     */
    @Transactional
    public int markOverdueAsMissed() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(1);
        List<OneOnOneMeeting> overdue = meetingRepository
                .findByStatusAndScheduledAtBefore(MeetingStatus.PLANNED, threshold);

        int updated = 0;
        for (OneOnOneMeeting meeting : overdue) {
            if (!Boolean.TRUE.equals(meeting.getMissed())) {
                meeting.setMissed(true);
                updated++;
            }
        }
        if (updated > 0) {
            meetingRepository.saveAll(overdue);
            log.info("Marked {} overdue 1:1 meeting(s) as missed", updated);
        }
        return updated;
    }
}
