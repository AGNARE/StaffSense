package com.staffsense.demo.api.survey;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.Survey;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.content.SurveyBuilderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Конструктор опросов (экран «Конструктор опроса»).
 *
 * <p>Отдельный контроллер рядом с {@link SurveyApiController}, а не правка
 * существующего: у прохождения другие права и другой контракт (сотрудник
 * видит форму, а не конструктор). Общий у них только путь ресурса.
 *
 * <p>Права: создание, правка черновика и публикация — {@code ADMIN} и
 * {@code HR}. Рядовой сотрудник получает 403.
 *
 * <p>Коды ответов: {@code 201} — черновик создан, {@code 200} — правка и
 * публикация, {@code 400} — некорректные данные (окно дат, пустое название,
 * охват без цели), {@code 404} — опроса нет, {@code 409} — публикация
 * невозможна или правка уже опубликованного опроса.
 */
@RestController
@RequestMapping("/api/v1/surveys")
@Tag(name = "Конструктор опросов", description = "Создание, правка черновика и публикация опроса")
public class SurveyBuilderApiController {

    private final SurveyBuilderService builderService;
    private final UserRepository userRepository;

    public SurveyBuilderApiController(SurveyBuilderService builderService,
                                      UserRepository userRepository) {
        this.builderService = builderService;
        this.userRepository = userRepository;
    }

    /** Создание черновика с вопросами. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<SurveyBuilderApiDtos.Detail> create(
            @Valid @RequestBody SurveyBuilderApiDtos.SaveRequest request,
            Authentication auth) {

        Survey survey = builderService.create(request, currentUser(auth));
        SurveyBuilderApiDtos.Detail body = detail(survey);
        return ResponseEntity.created(URI.create("/api/v1/surveys/" + survey.getId())).body(body);
    }

    /** Правка черновика: параметры и вопросы заменяются целиком. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public SurveyBuilderApiDtos.Detail update(
            @PathVariable Long id,
            @Valid @RequestBody SurveyBuilderApiDtos.SaveRequest request) {

        return detail(builderService.update(id, request));
    }

    /** Публикация черновика: опрос становится активным и виден сотрудникам. */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @ResponseStatus(HttpStatus.OK)
    public SurveyBuilderApiDtos.Detail publish(@PathVariable Long id) {
        return detail(builderService.publish(id));
    }

    /**
     * Опрос для формы конструктора.
     *
     * <p>Нужен, чтобы открыть сохранённый черновик на правку. Существующий
     * {@code GET /api/v1/surveys/{id}} отдаёт форму прохождения — в ней нет
     * типа опроса, охвата и признака обязательности.
     */
    @GetMapping("/{id}/builder")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public SurveyBuilderApiDtos.Detail builderForm(@PathVariable Long id) {
        return detail(builderService.require(id));
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private SurveyBuilderApiDtos.Detail detail(Survey survey) {
        return SurveyBuilderApiDtos.of(survey, builderService.questions(survey.getId()));
    }

    private User currentUser(Authentication auth) {
        if (auth == null) {
            throw new ResourceNotFoundException("Пользователь не определён");
        }
        return userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new ResourceNotFoundException("Пользователь не найден: " + auth.getName()));
    }
}
