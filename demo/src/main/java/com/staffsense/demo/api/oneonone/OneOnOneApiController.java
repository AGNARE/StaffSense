package com.staffsense.demo.api.oneonone;

import com.staffsense.demo.model.MeetingStatus;
import com.staffsense.demo.model.OneOnOneMeeting;
import com.staffsense.demo.model.User;
import com.staffsense.demo.service.OneOnOneService;
import com.staffsense.demo.service.TeamAccessService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST-контракт встреч 1:1 глазами участника.
 *
 * <p>Зачем: серверные страницы {@code /oneonone} живут на Thymeleaf, а новый
 * клиент про них не знает — у сотрудника не было ни одного способа увидеть
 * свои встречи. Контракт намеренно read-only: назначение, завершение и отмена
 * остаются в существующем {@link com.staffsense.demo.controller.OneOnOneController}
 * и {@link OneOnOneService}, чтобы правила доступа не разъехались.
 */
@RestController
@RequestMapping("/api/v1/oneonone")
@Tag(name = "Встречи 1:1", description = "Ближайшие встречи текущего пользователя")
public class OneOnOneApiController {

    private static final int MAX_LIMIT = 100;

    private final OneOnOneService oneOnOneService;
    private final TeamAccessService teamAccessService;

    public OneOnOneApiController(OneOnOneService oneOnOneService,
                                 TeamAccessService teamAccessService) {
        this.oneOnOneService = oneOnOneService;
        this.teamAccessService = teamAccessService;
    }

    /**
     * Мои встречи: сначала ближайшие будущие по возрастанию, затем прошедшие
     * по убыванию. Сотрудник видит только встречи, где он сотрудник,
     * руководитель — где он руководитель (обе стороны — из самого запроса).
     *
     * @param limit сколько строк вернуть, 1…100 (по умолчанию 20)
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public List<OneOnOneApiDtos.Meeting> my(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            Authentication auth) {

        User actor = teamAccessService.requireCurrentUser(auth);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);

        return oneOnOneService.getMyMeetings(actor).stream()
                .limit(safeLimit)
                .map(meeting -> toDto(meeting, actor))
                .toList();
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private OneOnOneApiDtos.Meeting toDto(OneOnOneMeeting meeting, User actor) {
        LocalDateTime now = LocalDateTime.now();
        boolean future = meeting.getScheduledAt() != null && !meeting.getScheduledAt().isBefore(now);
        boolean upcoming = future && meeting.getStatus() == MeetingStatus.PLANNED;

        boolean actorIsManager = meeting.getManager() != null
                && actor.getId() != null
                && actor.getId().equals(meeting.getManager().getId());

        return new OneOnOneApiDtos.Meeting(
                meeting.getId(),
                meeting.getEmployee().getId(),
                meeting.getEmployee().getFullName(),
                meeting.getManager().getId(),
                meeting.getManager().getFullName(),
                meeting.getScheduledAt(),
                meeting.getStatus().name(),
                meeting.getStatus().getTitle(),
                Boolean.TRUE.equals(meeting.getMissed()),
                meeting.getNotes(),
                meeting.getActionItems(),
                upcoming,
                teamAccessService.canManageMeeting(actor, meeting),
                actorIsManager ? "MANAGER" : "EMPLOYEE");
    }
}
