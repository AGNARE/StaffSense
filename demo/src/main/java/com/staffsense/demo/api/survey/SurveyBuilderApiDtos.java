package com.staffsense.demo.api.survey;

import com.staffsense.demo.model.QuestionType;
import com.staffsense.demo.model.Survey;
import com.staffsense.demo.model.SurveyQuestion;
import com.staffsense.demo.model.SurveyStatus;
import com.staffsense.demo.model.SurveyType;
import com.staffsense.demo.model.TargetScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Контракт конструктора опросов.
 *
 * <p>Экран «Конструктор опроса» создаёт и правит черновик целиком: параметры
 * (название, тип, охват, окно дат, анонимность) и упорядоченный список
 * вопросов. Поэтому тело запроса — один агрегат, а не отдельные ручки на
 * каждый вопрос: иначе «сохранить черновик» превращалось бы в несколько
 * запросов, часть которых могла примениться, а часть — нет.
 *
 * <p>Для чтения клиенту отдаётся {@link Detail} — тот же состав полей, что
 * уходит на сохранение, плюс идентификаторы и подписи для интерфейса, чтобы
 * форма редактирования открывалась без второй модели данных.
 */
public final class SurveyBuilderApiDtos {

    private SurveyBuilderApiDtos() {
    }

    /** Тело создания черновика и его правки. */
    public record SaveRequest(
            @NotBlank(message = "Название опроса обязательно")
            @Size(max = 255, message = "Название длиннее 255 символов") String title,

            @NotNull(message = "Тип опроса обязателен") SurveyType surveyType,

            @NotNull(message = "Охват обязателен") TargetScope targetScope,

            @Size(max = 100, message = "Название подразделения длиннее 100 символов")
            String targetDepartment,

            Long targetManagerId,
            Long targetUserId,

            @NotNull(message = "Дата начала обязательна") LocalDate startDate,
            @NotNull(message = "Дата окончания обязательна") LocalDate endDate,

            /** {@code true} — ответы сохраняются без привязки к сотруднику (BR-011). */
            Boolean anonymous,

            @Valid List<QuestionInput> questions
    ) {
    }

    /**
     * Вопрос в теле запроса.
     *
     * <p>Порядок вопросов задаётся позицией в массиве: клиент переставляет
     * строки конструктора и присылает их в нужном порядке, сервер нумерует
     * заново. Отдельное поле {@code orderIndex} не нужно — два источника
     * порядка всегда расходятся.
     */
    public record QuestionInput(
            @NotBlank(message = "Текст вопроса обязателен")
            @Size(max = 500, message = "Текст вопроса длиннее 500 символов") String text,

            @NotNull(message = "Тип вопроса обязателен") QuestionType type,

            /** {@code null} трактуется как обязательный вопрос. */
            Boolean required
    ) {
    }

    /** Вопрос в ответе. */
    public record Question(
            Long id,
            String text,
            String type,
            String typeTitle,
            boolean required,
            int orderIndex
    ) {
    }

    /** Опрос глазами конструктора. */
    public record Detail(
            Long id,
            String title,
            String surveyType,
            String surveyTypeTitle,
            String status,
            String statusTitle,
            String targetScope,
            String targetScopeTitle,
            String targetDepartment,
            Long targetManagerId,
            Long targetUserId,
            LocalDate startDate,
            LocalDate endDate,
            boolean anonymous,
            String createdBy,
            int questionCount,
            List<Question> questions,
            /** Черновик можно править (BR-013): опубликованный — нет. */
            boolean editable,
            /** Публикация возможна, если это черновик и в нём есть вопросы. */
            boolean publishable
    ) {
    }

    /** Сборка ответа из сущности и её вопросов. */
    public static Detail of(Survey survey, List<SurveyQuestion> questions) {
        List<Question> items = questions == null ? List.of() : questions.stream()
                .map(q -> new Question(
                        q.getId(),
                        q.getText(),
                        q.getType().name(),
                        questionTypeTitle(q.getType()),
                        q.isRequired(),
                        q.getOrderIndex() == null ? 0 : q.getOrderIndex()))
                .toList();

        boolean draft = survey.getStatus() == SurveyStatus.DRAFT;
        return new Detail(
                survey.getId(),
                survey.getTitle(),
                survey.getSurveyType().name(),
                surveyTypeTitle(survey.getSurveyType()),
                survey.getStatus().name(),
                statusTitle(survey.getStatus()),
                survey.getTargetScope().name(),
                targetScopeTitle(survey.getTargetScope()),
                survey.getTargetDepartment(),
                survey.getTargetManagerId(),
                survey.getTargetUserId(),
                survey.getStartDate(),
                survey.getEndDate(),
                survey.isAnonymous(),
                survey.getCreatedBy() != null ? survey.getCreatedBy().getFullName() : null,
                items.size(),
                items,
                draft,
                draft && !items.isEmpty());
    }

    // ── подписи для интерфейса ───────────────────────────────────────────
    // Хранятся на сервере, а не в клиенте: те же названия нужны серверным
    // страницам опросов, и второй список в браузере неизбежно разошёлся бы.

    static String surveyTypeTitle(SurveyType type) {
        return switch (type) {
            case PULSE -> "Пульс";
            case ENGAGEMENT -> "Вовлечённость";
            case ONBOARDING -> "Адаптация";
            case EXIT -> "Выходное интервью";
            case PERFORMANCE -> "Оценка";
            case TRAINING -> "Обучение";
            case WELLBEING -> "Благополучие";
            case MANAGER_360 -> "360°";
            case CULTURE -> "Культура";
            case CUSTOM -> "Свой";
        };
    }

    static String targetScopeTitle(TargetScope scope) {
        return switch (scope) {
            case COMPANY -> "Вся компания";
            case DEPARTMENT -> "Отдел";
            case TEAM -> "Команда";
            case INDIVIDUAL -> "Отдельные сотрудники";
        };
    }

    static String statusTitle(SurveyStatus status) {
        return switch (status) {
            case DRAFT -> "Черновик";
            case ACTIVE -> "Активен";
            case CLOSED -> "Закрыт";
        };
    }

    static String questionTypeTitle(QuestionType type) {
        return switch (type) {
            case LIKERT_5 -> "Шкала 1–5";
            case FREE_TEXT -> "Свободный ответ";
        };
    }
}
