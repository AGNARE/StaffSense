package com.staffsense.demo.service.content;

import com.staffsense.demo.api.survey.SurveyBuilderApiDtos;
import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.QuestionType;
import com.staffsense.demo.model.Survey;
import com.staffsense.demo.model.SurveyQuestion;
import com.staffsense.demo.model.SurveyStatus;
import com.staffsense.demo.model.TargetScope;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.SurveyQuestionRepository;
import com.staffsense.demo.repository.SurveyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Конструктор опросов: создание черновика, его правка и публикация.
 *
 * <p>Почему отдельный сервис, а не методы в {@code SurveyService}: сервис
 * опросов отвечает за прохождение и результаты и уже содержит свои правила
 * (окно доступности, единственный ответ на сотрудника). Смешивать с ними
 * правила конструктора — верный способ сломать одно, правя другое. Общими
 * остаются только репозитории, поэтому поведение существующих экранов
 * прохождения не меняется.
 *
 * <p>Правила, которые здесь собраны:
 * <ul>
 *   <li>окно дат: окончание не раньше начала;</li>
 *   <li>охват: выбранный тип охвата требует своей цели (отдел/руководитель/
 *       сотрудник) — иначе опрос не найдёт ни одного получателя;</li>
 *   <li>правка только черновика (BR-013): у опубликованного опроса менять
 *       вопросы нельзя, иначе ответы потеряют связь с вопросами;</li>
 *   <li>публикация: черновик с хотя бы одним вопросом, идемпотентно для уже
 *       активного опроса.</li>
 * </ul>
 */
@Service
public class SurveyBuilderService {

    private final SurveyRepository surveyRepository;
    private final SurveyQuestionRepository questionRepository;

    public SurveyBuilderService(SurveyRepository surveyRepository,
                                SurveyQuestionRepository questionRepository) {
        this.surveyRepository = surveyRepository;
        this.questionRepository = questionRepository;
    }

    /** Опрос по идентификатору или 404. */
    public Survey require(Long id) {
        return surveyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Опрос не найден: " + id));
    }

    /** Вопросы опроса в порядке отображения. */
    public List<SurveyQuestion> questions(Long surveyId) {
        return questionRepository.findBySurveyIdOrderByOrderIndex(surveyId);
    }

    /**
     * Создание черновика вместе с вопросами.
     *
     * <p>Статус всегда {@code DRAFT}: публикация — отдельное действие, чтобы
     * опрос не ушёл сотрудникам случайно в момент сохранения.
     */
    @Transactional
    public Survey create(SurveyBuilderApiDtos.SaveRequest request, User actor) {
        validate(request);

        Survey survey = Survey.builder()
                .title(request.title().trim())
                .createdBy(actor)
                .surveyType(request.surveyType())
                .targetScope(request.targetScope())
                .targetDepartment(trimToNull(request.targetDepartment()))
                .targetManagerId(request.targetManagerId())
                .targetUserId(request.targetUserId())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .anonymous(Boolean.TRUE.equals(request.anonymous()))
                .status(SurveyStatus.DRAFT)
                .build();
        surveyRepository.save(survey);
        replaceQuestions(survey, request.questions());
        return survey;
    }

    /**
     * Правка черновика. Опубликованный опрос не редактируется (BR-013).
     *
     * @throws IllegalStateException если опрос не в статусе {@code DRAFT}
     *                               (клиент получает 409)
     */
    @Transactional
    public Survey update(Long id, SurveyBuilderApiDtos.SaveRequest request) {
        Survey survey = require(id);
        if (survey.getStatus() != SurveyStatus.DRAFT) {
            throw new IllegalStateException(
                    "Редактировать можно только черновик, текущий статус: " + survey.getStatus()
                            + ". Опубликованный опрос не меняется — иначе ответы потеряют связь с вопросами.");
        }
        validate(request);

        survey.setTitle(request.title().trim());
        survey.setSurveyType(request.surveyType());
        survey.setTargetScope(request.targetScope());
        survey.setTargetDepartment(trimToNull(request.targetDepartment()));
        survey.setTargetManagerId(request.targetManagerId());
        survey.setTargetUserId(request.targetUserId());
        survey.setStartDate(request.startDate());
        survey.setEndDate(request.endDate());
        survey.setAnonymous(Boolean.TRUE.equals(request.anonymous()));
        surveyRepository.save(survey);

        replaceQuestions(survey, request.questions());
        return survey;
    }

    /**
     * Публикация: черновик становится активным и попадает в список опросов
     * сотрудников. Повторная публикация активного опроса — не ошибка
     * (идемпотентность: двойной клик не должен пугать пользователя).
     *
     * @throws IllegalStateException если опрос закрыт или в нём нет вопросов
     */
    @Transactional
    public Survey publish(Long id) {
        Survey survey = require(id);
        if (survey.getStatus() == SurveyStatus.ACTIVE) {
            return survey;
        }
        if (survey.getStatus() == SurveyStatus.CLOSED) {
            throw new IllegalStateException(
                    "Закрытый опрос нельзя опубликовать повторно: создайте новый.");
        }
        if (questions(id).isEmpty()) {
            throw new IllegalStateException(
                    "В опросе нет ни одного вопроса — публиковать нечего.");
        }

        survey.setStatus(SurveyStatus.ACTIVE);
        return surveyRepository.save(survey);
    }

    // ── вопросы ──────────────────────────────────────────────────────────

    /**
     * Полная замена набора вопросов.
     *
     * <p>Черновик ответов не имеет (проходить можно только активный опрос —
     * {@code SurveyService#requireCanTake}), поэтому удаление старых вопросов
     * не оставляет осиротевших ответов.
     */
    private void replaceQuestions(Survey survey, List<SurveyBuilderApiDtos.QuestionInput> inputs) {
        questionRepository.findBySurveyId(survey.getId()).forEach(questionRepository::delete);
        if (inputs == null || inputs.isEmpty()) {
            return;
        }
        int order = 0;
        for (SurveyBuilderApiDtos.QuestionInput input : inputs) {
            if (input == null || input.text() == null || input.text().isBlank()) {
                continue;
            }
            SurveyQuestion question = SurveyQuestion.builder()
                    .survey(survey)
                    .text(input.text().trim())
                    .type(input.type() == null ? QuestionType.LIKERT_5 : input.type())
                    .required(input.required() == null || input.required())
                    .orderIndex(order++)
                    .build();
            questionRepository.save(question);
        }
    }

    // ── проверки ─────────────────────────────────────────────────────────

    private void validate(SurveyBuilderApiDtos.SaveRequest request) {
        if (request.startDate() == null || request.endDate() == null) {
            throw new IllegalArgumentException("Укажите даты начала и окончания опроса.");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException(
                    "Дата окончания не может быть раньше даты начала.");
        }
        TargetScope scope = request.targetScope();
        if (scope == null) {
            throw new IllegalArgumentException("Укажите охват опроса.");
        }
        switch (scope) {
            case DEPARTMENT -> {
                if (trimToNull(request.targetDepartment()) == null) {
                    throw new IllegalArgumentException(
                            "Для охвата «Отдел» укажите подразделение.");
                }
            }
            case TEAM -> {
                if (request.targetManagerId() == null) {
                    throw new IllegalArgumentException(
                            "Для охвата «Команда» укажите руководителя.");
                }
            }
            case INDIVIDUAL -> {
                if (request.targetUserId() == null) {
                    throw new IllegalArgumentException(
                            "Для охвата «Отдельные сотрудники» укажите сотрудника.");
                }
            }
            case COMPANY -> {
                // Вся компания не требует дополнительной цели.
            }
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
