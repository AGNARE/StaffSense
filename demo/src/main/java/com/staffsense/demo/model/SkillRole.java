package com.staffsense.demo.model;

/**
 * Роль, для которой предназначен ИИ-скилл (FR-033).
 *
 * <p>Список отражает роли, под которые раскладывается каталог; он шире ролей
 * учётных записей, потому что скилл адресован специальности, а не правам доступа.
 */
public enum SkillRole {

    DESIGNER("Дизайнер"),
    FRONTEND("Фронтенд-разработчик"),
    BACKEND("Бэкенд-разработчик"),
    QA("Тестировщик"),
    PM("Руководитель проекта"),
    ANALYST("Аналитик"),
    OTHER("Другое");

    private final String title;

    SkillRole(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
