package com.staffsense.demo.model;

/**
 * Разделы внутреннего портала онбординга (модуль B).
 *
 * <p>Не путать с {@link SurveyType#ONBOARDING} — это шаблон опроса об адаптации,
 * отдельная сущность из модуля опросов.
 */
public enum OnboardingSection {

    ABOUT("О компании"),
    STRUCTURE("Структура компании"),
    RESPONSIBILITIES("Должностные инструкции"),
    POLICIES("Регламенты и политики"),
    FAQ("Частые вопросы"),
    CHECKLIST("Чек-лист новичка");

    private final String title;

    OnboardingSection(String title) {
        this.title = title;
    }

    /** Человекочитаемое название раздела для интерфейса. */
    public String getTitle() {
        return title;
    }
}
