package com.staffsense.demo.api.survey;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.QuestionType;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.Survey;
import com.staffsense.demo.model.SurveyParticipation;
import com.staffsense.demo.model.SurveyQuestion;
import com.staffsense.demo.model.SurveyStatus;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.SurveyParticipationRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.SurveyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Опросы: список, прохождение, результаты.
 *
 * <p>Права: прохождение — любой авторизованный, кому опрос адресован
 * ({@link SurveyService#requireCanTake}); результаты — ADMIN, HR, EXEC, PM.
 * Результаты отдаются только сводкой, персональных ответов в контракте нет.
 */
@RestController
@RequestMapping("/api/v1/surveys")
@Tag(name = "Опросы", description = "Прохождение опросов и агрегированные результаты")
public class SurveyApiController {

    private final SurveyService surveyService;
    private final UserRepository userRepository;
    private final SurveyParticipationRepository participationRepository;

    public SurveyApiController(SurveyService surveyService,
                               UserRepository userRepository,
                               SurveyParticipationRepository participationRepository) {
        this.surveyService = surveyService;
        this.userRepository = userRepository;
        this.participationRepository = participationRepository;
    }

    /** Опросы, доступные текущему пользователю. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<SurveyApiDtos.Item> list(Authentication auth) {
        User actor = currentUser(auth);
        boolean manages = actor.getRole() == Role.ADMIN || actor.getRole() == Role.HR;

        List<Survey> surveys = manages ? surveyService.findAll() : surveyService.getVisibleForEmployee(actor);

        return surveys.stream()
                .map(survey -> {
                    boolean taken = participationRepository.existsBySurveyIdAndUserId(survey.getId(), actor.getId());
                    return new SurveyApiDtos.Item(
                            survey.getId(),
                            survey.getTitle(),
                            survey.getStatus().name(),
                            survey.getStatus().name(),
                            survey.getSurveyType().name(),
                            survey.isAnonymous(),
                            survey.getStartDate(),
                            survey.getEndDate(),
                            surveyService.getQuestions(survey.getId()).size(),
                            surveyService.getResponseCount(survey.getId()),
                            canTake(survey, taken),
                            taken);
                })
                .toList();
    }

    /** Опрос с вопросами для прохождения. */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public SurveyApiDtos.Form form(@PathVariable Long id, Authentication auth) {
        User actor = currentUser(auth);
        Survey survey = surveyService.findById(id);
        boolean taken = participationRepository.existsBySurveyIdAndUserId(id, actor.getId());

        List<SurveyApiDtos.Question> questions = surveyService.getQuestions(id).stream()
                .map(q -> new SurveyApiDtos.Question(
                        q.getId(), q.getText(), q.getType().name(),
                        q.getOrderIndex() == null ? 0 : q.getOrderIndex()))
                .toList();

        return new SurveyApiDtos.Form(
                survey.getId(),
                survey.getTitle(),
                survey.getStatus().name(),
                survey.isAnonymous(),
                survey.getStartDate(),
                survey.getEndDate(),
                questions,
                canTake(survey, taken),
                taken);
    }

    /**
     * Отправка ответов.
     *
     * <p>Два адреса — один обработчик: клиент уже вызывает {@code /responses},
     * а контракт сценария сотрудника упоминает {@code /submit}. Дублировать
     * правила нельзя, поэтому оба пути ведут сюда.
     */
    @PostMapping({"/{id}/responses", "/{id}/submit"})
    @PreAuthorize("isAuthenticated()")
    public SurveyApiDtos.SubmitResult submit(@PathVariable Long id,
                                             @RequestBody SurveyApiDtos.SubmitRequest request,
                                             Authentication auth) {
        User actor = currentUser(auth);
        Survey survey = surveyService.findById(id);

        // Валидацию окна прохождения и прав оставляем сервису: правила не дублируем.
        surveyService.requireCanTake(survey, actor);

        Map<Long, String> answers = request.answers() == null ? Map.of() : request.answers();
        surveyService.submitResponse(survey, actor, answers);

        return new SurveyApiDtos.SubmitResult(id, answers.size());
    }

    /** Агрегированные результаты опроса. */
    @GetMapping("/{id}/results")
    @PreAuthorize("hasAnyRole('ADMIN','HR','EXEC','PM')")
    public SurveyApiDtos.Results results(@PathVariable Long id) {
        Survey survey = surveyService.findById(id);
        return SurveyApiDtos.fromAnalytics(survey, surveyService.getAnalytics(id));
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private boolean canTake(Survey survey, boolean taken) {
        return !taken
                && survey.getStatus() == SurveyStatus.ACTIVE
                && survey.getStartDate() != null
                && survey.getEndDate() != null
                && !java.time.LocalDate.now().isBefore(survey.getStartDate())
                && !java.time.LocalDate.now().isAfter(survey.getEndDate());
    }

    private User currentUser(Authentication auth) {
        if (auth == null) {
            throw new ResourceNotFoundException("Пользователь не определён");
        }
        return userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new ResourceNotFoundException("Пользователь не найден: " + auth.getName()));
    }
}
