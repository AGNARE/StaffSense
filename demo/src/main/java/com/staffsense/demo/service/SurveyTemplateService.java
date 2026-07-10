package com.staffsense.demo.service;

import com.staffsense.demo.model.QuestionType;
import com.staffsense.demo.model.SurveyQuestion;
import com.staffsense.demo.model.SurveyType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Predefined survey templates. Each template has a key (SurveyType name)
 * and returns a list of SurveyQuestions (unsaved — just populated with
 * text, type, and orderIndex).
 */
@Service
public class SurveyTemplateService {

    private static final Map<String, List<SurveyQuestion>> TEMPLATES = Map.ofEntries(
        Map.entry("PULSE", List.of(
            q("Насколько вы удовлетворены своей текущей загрузкой?", QuestionType.LIKERT_5, 0),
            q("Чувствуете ли вы поддержку от коллег и руководства?", QuestionType.LIKERT_5, 1),
            q("Понимаете ли вы свои цели на текущий месяц?", QuestionType.LIKERT_5, 2),
            q("Есть ли у вас все необходимые ресурсы для работы?", QuestionType.LIKERT_5, 3),
            q("Как бы вы оценили общее настроение в команде?", QuestionType.LIKERT_5, 4),
            q("Что бы вы хотели улучшить в ближайшее время?", QuestionType.FREE_TEXT, 5)
        )),
        Map.entry("ENGAGEMENT", List.of(
            q("Я горжусь тем, что работаю в этой компании", QuestionType.LIKERT_5, 0),
            q("Я рекомендую компанию как место работы друзьям", QuestionType.LIKERT_5, 1),
            q("Моя работа даёт мне чувство удовлетворения", QuestionType.LIKERT_5, 2),
            q("Я вижу своё будущее в этой компании", QuestionType.LIKERT_5, 3),
            q("Руководство эффективно коммуницирует стратегию", QuestionType.LIKERT_5, 4),
            q("Мой вклад ценят и признают", QuestionType.LIKERT_5, 5),
            q("У меня есть возможности для роста и развития", QuestionType.LIKERT_5, 6),
            q("Баланс работы и личной жизни соблюдается", QuestionType.LIKERT_5, 7),
            q("Что могло бы повысить вашу вовлечённость?", QuestionType.FREE_TEXT, 8)
        )),
        Map.entry("EXIT", List.of(
            q("Как бы вы оценили общий опыт работы в компании?", QuestionType.LIKERT_5, 0),
            q("Была ли ваша работа интересной и содержательной?", QuestionType.LIKERT_5, 1),
            q("Чувствовали ли вы поддержку от руководства?", QuestionType.LIKERT_5, 2),
            q("Была ли конкурентоспособной ваша зарплата?", QuestionType.LIKERT_5, 3),
            q("Порекомендуете ли вы компанию другим?", QuestionType.LIKERT_5, 4),
            q("Какая основная причина вашего ухода?", QuestionType.FREE_TEXT, 5),
            q("Что можно было бы улучшить в компании?", QuestionType.FREE_TEXT, 6),
            q("Что вам больше всего нравилось в работе?", QuestionType.FREE_TEXT, 7),
            q("Вернулись бы вы, если бы обстоятельства изменились?", QuestionType.FREE_TEXT, 8)
        )),
        Map.entry("ONBOARDING", List.of(
            q("Процесс онбординга был понятным и структурированным", QuestionType.LIKERT_5, 0),
            q("Я получил достаточно информации о компании и культуре", QuestionType.LIKERT_5, 1),
            q("Мой наставник или бадди был полезен", QuestionType.LIKERT_5, 2),
            q("Я понимаю свою роль и ожидаемые результаты", QuestionType.LIKERT_5, 3),
            q("У меня есть все необходимые доступы и инструменты", QuestionType.LIKERT_5, 4),
            q("Я чувствую себя частью команды", QuestionType.LIKERT_5, 5),
            q("Что в онбординге было наиболее полезным?", QuestionType.FREE_TEXT, 6),
            q("Что можно улучшить для будущих новичков?", QuestionType.FREE_TEXT, 7)
        )),
        Map.entry("PERFORMANCE", List.of(
            q("Я понимаю свои KPI и цели на период", QuestionType.LIKERT_5, 0),
            q("Я получил достаточно обратной связи за период", QuestionType.LIKERT_5, 1),
            q("Мне хватает ресурсов для выполнения задач", QuestionType.LIKERT_5, 2),
            q("Процессы в команде эффективны", QuestionType.LIKERT_5, 3),
            q("Я успеваю выполнять задачи в срок", QuestionType.LIKERT_5, 4),
            q("Мои сильные стороны используются эффективно", QuestionType.LIKERT_5, 5),
            q("Я знаю, над чем мне нужно работать дальше", QuestionType.LIKERT_5, 6),
            q("Какие барьеры мешают работать эффективнее?", QuestionType.FREE_TEXT, 7)
        )),
        Map.entry("TRAINING", List.of(
            q("Материал был структурирован и понятен", QuestionType.LIKERT_5, 0),
            q("Тренер или преподаватель был компетентен", QuestionType.LIKERT_5, 1),
            q("Я смогу применить полученные знания на практике", QuestionType.LIKERT_5, 2),
            q("Длительность тренинга была оптимальной", QuestionType.LIKERT_5, 3),
            q("Я рекомендую этот тренинг коллегам", QuestionType.LIKERT_5, 4),
            q("Что было самым полезным в тренинге?", QuestionType.FREE_TEXT, 5),
            q("Что можно улучшить?", QuestionType.FREE_TEXT, 6)
        )),
        Map.entry("WELLBEING", List.of(
            q("Я хорошо сплю и восстанавливаюсь после работы", QuestionType.LIKERT_5, 0),
            q("Я чувствую себя энергичным на работе", QuestionType.LIKERT_5, 1),
            q("Я могу вовремя отключаться от работы", QuestionType.LIKERT_5, 2),
            q("Чувствую поддержку от коллег и руководства", QuestionType.LIKERT_5, 3),
            q("Моя нагрузка в последнее время была комфортной", QuestionType.LIKERT_5, 4),
            q("Я чувствую, что контролирую свой рабочий график", QuestionType.LIKERT_5, 5),
            q("Что вызывает у вас наибольший стресс на работе?", QuestionType.FREE_TEXT, 6)
        )),
        Map.entry("MANAGER_360", List.of(
            q("Мой руководитель даёт чёткую обратную связь", QuestionType.LIKERT_5, 0),
            q("Я чувствую поддержку в своём развитии", QuestionType.LIKERT_5, 1),
            q("Коммуникация с руководителем прозрачна", QuestionType.LIKERT_5, 2),
            q("Руководитель признаёт мои достижения", QuestionType.LIKERT_5, 3),
            q("Я могу обсуждать проблемы без страха", QuestionType.LIKERT_5, 4),
            q("Руководитель помогает расставлять приоритеты", QuestionType.LIKERT_5, 5),
            q("В команде справедливо распределяется нагрузка", QuestionType.LIKERT_5, 6),
            q("Я доверяю решениям руководителя", QuestionType.LIKERT_5, 7),
            q("Что руководитель может улучшить?", QuestionType.FREE_TEXT, 8),
            q("Что работает хорошо во взаимодействии?", QuestionType.FREE_TEXT, 9)
        )),
        Map.entry("CULTURE", List.of(
            q("Ценности компании совпадают с моими личными", QuestionType.LIKERT_5, 0),
            q("В компании поощряется открытость и прозрачность", QuestionType.LIKERT_5, 1),
            q("Разнообразие и инклюзивность — реальные ценности", QuestionType.LIKERT_5, 2),
            q("Я могу высказывать своё мнение без опасений", QuestionType.LIKERT_5, 3),
            q("В компании здоровое отношение к ошибкам", QuestionType.LIKERT_5, 4),
            q("Инновации и идеи поощряются", QuestionType.LIKERT_5, 5),
            q("Социальная ответственность компании важна", QuestionType.LIKERT_5, 6),
            q("Какие аспекты культуры нужно укрепить?", QuestionType.FREE_TEXT, 7),
            q("Что вам нравится в культуре компании?", QuestionType.FREE_TEXT, 8)
        ))
    );

    /**
     * Returns the question template for the given SurveyType key.
     * For CUSTOM type, returns an empty list.
     */
    public List<SurveyQuestion> getTemplate(String surveyTypeKey) {
        return TEMPLATES.getOrDefault(surveyTypeKey, List.of());
    }

    /**
     * Returns all available template keys (SurveyType names except CUSTOM).
     */
    public List<String> getAvailableTemplateKeys() {
        return List.of("PULSE", "ENGAGEMENT", "EXIT", "ONBOARDING",
                "PERFORMANCE", "TRAINING", "WELLBEING", "MANAGER_360", "CULTURE");
    }

    /**
     * Returns the display name (Russian) for a template key.
     */
    public String getTemplateDisplayName(String key) {
        return switch (key) {
            case "PULSE" -> "Пульс-опрос (5 вопросов)";
            case "ENGAGEMENT" -> "Вовлечённость (9 вопросов)";
            case "EXIT" -> "Выходное интервью (10 вопросов)";
            case "ONBOARDING" -> "Адаптация (8 вопросов)";
            case "PERFORMANCE" -> "Оценка эффективности (8 вопросов)";
            case "TRAINING" -> "Обратная связь по тренингу (7 вопросов)";
            case "WELLBEING" -> "Благополучие (7 вопросов)";
            case "MANAGER_360" -> "Оценка руководителя (10 вопросов)";
            case "CULTURE" -> "Корпоративная культура (9 вопросов)";
            default -> key;
        };
    }

    public SurveyType getSurveyTypeForTemplate(String key) {
        return switch (key) {
            case "PULSE" -> SurveyType.PULSE;
            case "ENGAGEMENT" -> SurveyType.ENGAGEMENT;
            case "EXIT" -> SurveyType.EXIT;
            case "ONBOARDING" -> SurveyType.ONBOARDING;
            case "PERFORMANCE" -> SurveyType.PERFORMANCE;
            case "TRAINING" -> SurveyType.TRAINING;
            case "WELLBEING" -> SurveyType.WELLBEING;
            case "MANAGER_360" -> SurveyType.MANAGER_360;
            case "CULTURE" -> SurveyType.CULTURE;
            default -> SurveyType.CUSTOM;
        };
    }

    // Helper to create question template objects
    private static SurveyQuestion q(String text, QuestionType type, int order) {
        return SurveyQuestion.builder()
                .text(text)
                .type(type)
                .orderIndex(order)
                .build();
    }
}
