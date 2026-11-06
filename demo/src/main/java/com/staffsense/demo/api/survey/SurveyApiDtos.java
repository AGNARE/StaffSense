package com.staffsense.demo.api.survey;

import com.staffsense.demo.service.SurveyService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DTO опросов и результатов.
 *
 * <p>Анонимность — часть контракта: результаты отдаются только агрегированно
 * (среднее, распределение, количество ответов). Текстовые ответы свободной
 * формы возвращаются без автора и без времени — иначе анонимность обходится
 * сопоставлением времени с табелем (это уже описано как риск в аудите).
 */
public final class SurveyApiDtos {

    private SurveyApiDtos() {
    }

    /** Строка списка опросов. */
    public record Item(
            Long id,
            String title,
            String status,
            String statusTitle,
            String type,
            boolean anonymous,
            LocalDate startDate,
            LocalDate endDate,
            int questionCount,
            long responseCount,
            boolean canTake,
            boolean alreadyTaken
    ) {
    }

    /** Вопрос в форме прохождения. */
    public record Question(Long id, String text, String type, int orderIndex) {
    }

    /** Опрос для прохождения. */
    public record Form(
            Long id,
            String title,
            String status,
            boolean anonymous,
            LocalDate startDate,
            LocalDate endDate,
            List<Question> questions,
            boolean canTake,
            boolean alreadyTaken
    ) {
    }

    /** Тело запроса с ответами: идентификатор вопроса → значение. */
    public record SubmitRequest(Map<Long, String> answers) {
    }

    /** Результат прохождения. */
    public record SubmitResult(Long surveyId, int answered) {
    }

    /** Результаты: только агрегаты. */
    public record Results(
            Long surveyId,
            String title,
            boolean anonymous,
            int totalResponses,
            List<QuestionResult> questions,
            List<FreeText> freeTexts
    ) {
    }

    public record QuestionResult(
            Long questionId,
            String text,
            int responseCount,
            double average,
            int min,
            int max,
            long[] distribution
    ) {
    }

    /** Текстовый ответ без автора и даты — см. комментарий к классу. */
    public record FreeText(String question, String answer) {
    }

    public static Results fromAnalytics(com.staffsense.demo.model.Survey survey,
                                        SurveyService.SurveyAnalytics analytics) {
        return new Results(
                survey.getId(),
                survey.getTitle(),
                survey.isAnonymous(),
                analytics.totalResponses(),
                analytics.questions().stream()
                        .map(q -> new QuestionResult(q.questionId(), q.questionText(),
                                q.responseCount(), q.average(), q.min(), q.max(), q.distribution()))
                        .toList(),
                analytics.freeTexts().stream()
                        .map(f -> new FreeText(f.questionText(), f.answer()))
                        .toList());
    }
}
