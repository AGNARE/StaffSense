package com.staffsense.demo.service;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.ServiceMemoRepository;
import com.staffsense.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Служебные записки (модуль C).
 *
 * <p>Бизнес-правила:
 * <ul>
 *   <li><b>BR-029</b> — подать записку может любой сотрудник;</li>
 *   <li>маршрут по ТЗ (<b>BR-003</b>): {@code PENDING_PM → PENDING_HR → APPROVED},
 *       отклонение на любом этапе → {@code REJECTED};</li>
 *   <li>автор может <b>редактировать и отозвать</b> записку, пока нет финального решения;</li>
 *   <li><b>BR-030</b> — утверждённый больничный/отпуск автоматически попадает в табель.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ServiceMemoService {

    private static final Logger log = LoggerFactory.getLogger(ServiceMemoService.class);

    private final ServiceMemoRepository memoRepository;
    private final UserRepository userRepository;
    private final TeamAccessService teamAccessService;
    private final TimesheetService timesheetService;

    // ── Подача и редактирование ──────────────────────────────────────────

    /** Подать записку — может любой сотрудник. */
    @Transactional
    public ServiceMemo submit(User author, MemoType type, LocalDate start, LocalDate end, String reason) {
        validatePeriod(start, end);

        ServiceMemo memo = ServiceMemo.builder()
                .user(author)
                .memoType(type)
                .startDate(start)
                .endDate(end)
                .reason(reason)
                .status(MemoStatus.PENDING_PM)
                .createdAt(LocalDateTime.now())
                .build();

        ServiceMemo saved = memoRepository.save(memo);
        log.info("Memo {} submitted by {} ({} {}..{})",
                saved.getId(), author.getEmail(), type, start, end);
        return saved;
    }

    /** Редактирование автором, пока нет финального решения. */
    @Transactional
    public ServiceMemo update(User actor, Long memoId, MemoType type,
                              LocalDate start, LocalDate end, String reason) {
        ServiceMemo memo = findById(memoId);
        requireAuthor(actor, memo);
        requireEditable(memo);
        validatePeriod(start, end);

        memo.setMemoType(type);
        memo.setStartDate(start);
        memo.setEndDate(end);
        memo.setReason(reason);
        return memoRepository.save(memo);
    }

    /** Отзыв автором, пока нет финального решения. */
    @Transactional
    public ServiceMemo cancel(User actor, Long memoId) {
        ServiceMemo memo = findById(memoId);
        requireAuthor(actor, memo);
        requireEditable(memo);

        memo.setStatus(MemoStatus.CANCELLED);
        log.info("Memo {} cancelled by author {}", memoId, actor.getEmail());
        return memoRepository.save(memo);
    }

    // ── Согласование ─────────────────────────────────────────────────────

    /** Виза руководителя: PENDING_PM → PENDING_HR. */
    @Transactional
    public ServiceMemo approveByPm(User actor, Long memoId) {
        ServiceMemo memo = findById(memoId);
        if (memo.getStatus() != MemoStatus.PENDING_PM) {
            throw new IllegalStateException("Записка не находится на визе руководителя");
        }
        requirePmOfAuthor(actor, memo);

        memo.setStatus(MemoStatus.PENDING_HR);
        memo.setPmDecisionBy(actor);
        memo.setPmDecisionAt(LocalDateTime.now());
        log.info("Memo {} approved by PM {}", memoId, actor.getEmail());
        return memoRepository.save(memo);
    }

    /** Утверждение HR: PENDING_HR → APPROVED, затем простановка дней в табеле (BR-030). */
    @Transactional
    public ServiceMemo approveByHr(User actor, Long memoId) {
        ServiceMemo memo = findById(memoId);
        if (memo.getStatus() != MemoStatus.PENDING_HR) {
            throw new IllegalStateException("Записка не находится на утверждении HR");
        }
        requireHr(actor);

        // BR-028: утверждение записки не должно переписывать табель закрытого месяца —
        // иначе отчёт по закрытому периоду перестанет соответствовать данным.
        if (timesheetService.isRangeLocked(memo.getUser(), memo.getStartDate(), memo.getEndDate())) {
            throw new IllegalStateException(
                    "Период закрыт — утверждение записки изменило бы табель закрытого месяца (BR-028)");
        }

        memo.setStatus(MemoStatus.APPROVED);
        memo.setHrDecisionBy(actor);
        memo.setHrDecisionAt(LocalDateTime.now());
        ServiceMemo saved = memoRepository.save(memo);

        // BR-030: утверждённый больничный/отпуск попадает в табель и влияет на нагрузку.
        timesheetService.applyApprovedMemo(saved);
        return saved;
    }

    /** Отклонение: доступно на этапе визы PM или утверждения HR. */
    @Transactional
    public ServiceMemo reject(User actor, Long memoId, String reason) {
        ServiceMemo memo = findById(memoId);

        if (memo.getStatus() == MemoStatus.PENDING_PM) {
            requirePmOfAuthor(actor, memo);
            memo.setPmDecisionBy(actor);
            memo.setPmDecisionAt(LocalDateTime.now());
        } else if (memo.getStatus() == MemoStatus.PENDING_HR) {
            requireHr(actor);
            memo.setHrDecisionBy(actor);
            memo.setHrDecisionAt(LocalDateTime.now());
        } else {
            throw new IllegalStateException("Записка уже не может быть отклонена");
        }

        memo.setStatus(MemoStatus.REJECTED);
        memo.setRejectionReason(reason);
        log.info("Memo {} rejected by {}", memoId, actor.getEmail());
        return memoRepository.save(memo);
    }

    // ── Выборки ──────────────────────────────────────────────────────────

    public ServiceMemo findById(Long id) {
        return memoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Записка не найдена: " + id));
    }

    public List<ServiceMemo> getMyMemos(User actor) {
        return memoRepository.findByUserIdOrderByCreatedAtDesc(actor.getId());
    }

    /** Записки, видимые пользователю: свои + (PM) команды + (HR/ADMIN/EXEC) все. */
    public List<ServiceMemo> getVisibleMemos(User actor) {
        return switch (actor.getRole()) {
            case ADMIN, HR, EXEC -> memoRepository.findAllByOrderByCreatedAtDesc();
            case PM -> {
                List<Long> ids = new ArrayList<>();
                ids.add(actor.getId());
                ids.addAll(userRepository.findActiveByManager(actor.getId(), true).stream()
                        .map(User::getId).toList());
                yield memoRepository.findByUserIdInOrderByCreatedAtDesc(ids);
            }
            case EMPLOYEE -> memoRepository.findByUserIdOrderByCreatedAtDesc(actor.getId());
        };
    }

    /** Записки, ожидающие визы конкретного руководителя. */
    public List<ServiceMemo> getPendingForPm(User actor) {
        return memoRepository.findByStatusOrderByCreatedAtAsc(MemoStatus.PENDING_PM).stream()
                .filter(m -> actor.getRole() == Role.ADMIN
                        || teamAccessService.isEmployeeInTeamOf(actor, m.getUser()))
                .toList();
    }

    /** Записки, ожидающие утверждения HR. */
    public List<ServiceMemo> getPendingForHr(User actor) {
        if (actor.getRole() != Role.HR && actor.getRole() != Role.ADMIN) {
            return List.of();
        }
        return memoRepository.findByStatusOrderByCreatedAtAsc(MemoStatus.PENDING_HR);
    }

    /**
     * Проверка для формы редактирования: править записку может только её автор
     * и только до финального решения (BR-029).
     *
     * <p>Раньше {@code GET /memos/{id}/edit} не проверял ни автора, ни редактируемость:
     * форму чужой записки открывал любой аутентифицированный пользователь, хотя
     * сохранение уже было защищено.
     */
    public void requireEditableBy(User actor, ServiceMemo memo) {
        requireAuthor(actor, memo);
        requireEditable(memo);
    }

    // ── Проверки ─────────────────────────────────────────────────────────

    private void validatePeriod(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Укажите период записки");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("Дата окончания не может быть раньше даты начала");
        }
    }

    private void requireAuthor(User actor, ServiceMemo memo) {
        if (actor.getId() == null || !actor.getId().equals(memo.getUser().getId())) {
            throw new AccessDeniedException("Редактировать или отозвать записку может только её автор");
        }
    }

    private void requireEditable(ServiceMemo memo) {
        if (!memo.isEditable()) {
            throw new IllegalStateException("Записка уже получила решение — изменение невозможно");
        }
    }

    private void requirePmOfAuthor(User actor, ServiceMemo memo) {
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        boolean isPm = actor.getRole() == Role.PM
                && teamAccessService.isEmployeeInTeamOf(actor, memo.getUser());
        if (!isPm) {
            throw new AccessDeniedException("Визировать записку может руководитель автора");
        }
    }

    private void requireHr(User actor) {
        if (actor.getRole() != Role.HR && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Утверждать записку может только HR");
        }
    }
}
