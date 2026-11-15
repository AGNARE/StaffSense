package com.staffsense.demo.api.memo;

import com.staffsense.demo.model.MemoStatus;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.ServiceMemo;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.ServiceMemoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Служебные записки: список, карточка, создание, согласование.
 *
 * <p>Правила маршрута (BR-003, BR-029) уже живут в {@link ServiceMemoService};
 * контроллер их не повторяет, а переводит в JSON. Действия, доступные
 * конкретному пользователю, считаются здесь и отдаются как {@code allowedActions}:
 * иначе клиент повторял бы маршрут согласования у себя.
 */
@RestController
@RequestMapping("/api/v1/memos")
@Tag(name = "Служебные записки", description = "Заявки на отпуск и больничный, согласование")
public class MemoApiController {

    private final ServiceMemoService memoService;
    private final UserRepository userRepository;

    public MemoApiController(ServiceMemoService memoService, UserRepository userRepository) {
        this.memoService = memoService;
        this.userRepository = userRepository;
    }

    /** Записки, видимые текущему пользователю (свои или на согласовании). */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<MemoApiDtos.Memo> list(Authentication auth) {
        User actor = currentUser(auth);
        List<ServiceMemo> memos = memoService.getVisibleMemos(actor);
        return memos.stream().map(memo -> toDto(memo, actor)).toList();
    }

    /**
     * Записки, ожидающие решения этого пользователя: руководителю — на визу,
     * HR — на утверждение.
     */
    @GetMapping("/pending")
    @PreAuthorize("isAuthenticated()")
    public List<MemoApiDtos.Memo> pending(Authentication auth) {
        User actor = currentUser(auth);
        List<ServiceMemo> result = new ArrayList<>();
        if (actor.getRole() == Role.PM) {
            result.addAll(memoService.getPendingForPm(actor));
        }
        if (actor.getRole() == Role.HR || actor.getRole() == Role.ADMIN) {
            result.addAll(memoService.getPendingForHr(actor));
        }
        return result.stream().map(memo -> toDto(memo, actor)).toList();
    }

    /**
     * Карточка записки. Чужую запись сотрудник не откроет и по прямому id:
     * видимость проверяется тем же правилом, что и у списка.
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public MemoApiDtos.Memo byId(@PathVariable Long id, Authentication auth) {
        User actor = currentUser(auth);
        ServiceMemo memo = memoService.findById(id);
        memoService.requireCanView(actor, memo);
        return toDto(memo, actor);
    }

    /** Создание записки автором. */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public MemoApiDtos.Memo create(@Valid @RequestBody MemoApiDtos.CreateRequest request,
                                   Authentication auth) {
        User actor = currentUser(auth);
        ServiceMemo memo = memoService.submit(
                actor, request.type(), request.startDate(), request.endDate(), request.reason());
        return toDto(memo, actor);
    }

    /** Виза руководителя. */
    @PostMapping("/{id}/approve-pm")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public MemoApiDtos.Memo approvePm(@PathVariable Long id, Authentication auth) {
        User actor = currentUser(auth);
        return toDto(memoService.approveByPm(actor, id), actor);
    }

    /** Утверждение HR. */
    @PostMapping("/{id}/approve-hr")
    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    public MemoApiDtos.Memo approveHr(@PathVariable Long id, Authentication auth) {
        User actor = currentUser(auth);
        return toDto(memoService.approveByHr(actor, id), actor);
    }

    /** Отклонение с обязательной причиной. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('PM','HR','ADMIN')")
    public MemoApiDtos.Memo reject(@PathVariable Long id,
                                   @Valid @RequestBody MemoApiDtos.RejectRequest request,
                                   Authentication auth) {
        User actor = currentUser(auth);
        return toDto(memoService.reject(actor, id, request.reason()), actor);
    }

    /** Отзыв автором до финального решения. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public MemoApiDtos.Memo cancel(@PathVariable Long id, Authentication auth) {
        User actor = currentUser(auth);
        return toDto(memoService.cancel(actor, id), actor);
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private User currentUser(Authentication auth) {
        if (auth == null) {
            throw new com.staffsense.demo.exception.ResourceNotFoundException("Пользователь не определён");
        }
        return userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new com.staffsense.demo.exception.ResourceNotFoundException(
                        "Пользователь не найден: " + auth.getName()));
    }

    private MemoApiDtos.Memo toDto(ServiceMemo memo, User actor) {
        long days = memo.getStartDate() == null || memo.getEndDate() == null
                ? 0
                : ChronoUnit.DAYS.between(memo.getStartDate(), memo.getEndDate()) + 1;

        return new MemoApiDtos.Memo(
                memo.getId(),
                memo.getUser().getId(),
                memo.getUser().getFullName(),
                memo.getUser().getDepartment(),
                memo.getMemoType().name(),
                memo.getMemoType().getTitle(),
                memo.getStartDate(),
                memo.getEndDate(),
                Math.max(days, 0),
                memo.getReason(),
                memo.getStatus().name(),
                memo.getStatus().getTitle(),
                memo.getCreatedAt(),
                memo.getPmDecisionBy() != null ? memo.getPmDecisionBy().getFullName() : null,
                memo.getPmDecisionAt(),
                memo.getHrDecisionBy() != null ? memo.getHrDecisionBy().getFullName() : null,
                memo.getHrDecisionAt(),
                memo.getRejectionReason(),
                allowedActions(memo, actor));
    }

    /**
     * Действия пользователя по записке. Повторяют правила сервиса, потому что
     * интерфейсу нужно знать, какие кнопки рисовать, а сервис бросает исключение.
     */
    private List<String> allowedActions(ServiceMemo memo, User actor) {
        List<String> actions = new ArrayList<>();
        boolean author = memo.getUser().getId().equals(actor.getId());
        MemoStatus status = memo.getStatus();

        if (author && !status.isFinal()) {
            actions.add("cancel");
            actions.add("edit");
        }
        if (status == MemoStatus.PENDING_PM
                && (actor.getRole() == Role.ADMIN
                    || (actor.getRole() == Role.PM && !author))) {
            actions.add("approve-pm");
            actions.add("reject");
        }
        if (status == MemoStatus.PENDING_HR
                && (actor.getRole() == Role.ADMIN || actor.getRole() == Role.HR)) {
            actions.add("approve-hr");
            actions.add("reject");
        }
        return actions;
    }
}
