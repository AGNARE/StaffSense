package com.staffsense.demo.api.me;

import com.staffsense.demo.model.Role;

import java.util.ArrayList;
import java.util.List;

/**
 * Какие экраны доступны роли — единый источник правды для навигации.
 *
 * <p>Зачем это на сервере, а не в клиенте: право доступа уже описано здесь
 * ({@code SecurityConfig}), и если меню собирать в браузере, появится второй
 * список правил, который разойдётся с первым. Клиент рисует только то, что
 * прислал сервер.
 *
 * <p>Экраны разделены по целям роли:
 * <ul>
 *   <li><b>Сотрудник</b> — понять свои задачи и пройти положенное: профиль,
 *       опросы, записки, портал онбординга.</li>
 *   <li><b>Руководитель</b> — увидеть свою команду и её сигналы, согласовать
 *       записки, вести проекты.</li>
 *   <li><b>HR</b> — вести людей: кадры, документы, опросы, риски, онбординг.</li>
 *   <li><b>Руководитель компании (EXEC)</b> — смотреть картину целиком, без
 *       операционных действий.</li>
 *   <li><b>Администратор</b> — всё перечисленное плюс настройка системы.</li>
 * </ul>
 */
public final class RoleScreens {

    /** Экран в навигации. */
    public record Screen(String id, String title, String route, String group) {
    }

    private RoleScreens() {
    }

    /** Навигация для роли: порядок и состав заданы здесь. */
    public static List<Screen> forRole(Role role) {
        List<Screen> screens = new ArrayList<>();

        // Общее для всех: свой кабинет
        screens.add(new Screen("me", "Мой кабинет", "#/me", "люди"));

        if (role == Role.EMPLOYEE) {
            screens.add(new Screen("surveys", "Опросы", "#/surveys", "люди"));
            screens.add(new Screen("memos", "Мои записки", "#/memos", "работа"));
            screens.add(new Screen("onboarding", "Портал", "#/onboarding", "люди"));
            return screens;
        }

        // EXEC, HR, PM, ADMIN — управленческая часть
        screens.add(new Screen("employees", "Кадры", "#/employees", "люди"));
        screens.add(new Screen("onboarding", "Портал", "#/onboarding", "люди"));
        screens.add(new Screen("surveys", "Опросы", "#/surveys", "люди"));
        screens.add(new Screen("memos", "Записки", "#/memos", "люди"));
        screens.add(new Screen("projects", "Проекты", "#/projects", "работа"));
        screens.add(new Screen("risk", "Риски увольнений", "#/risk", "работа"));
        screens.add(new Screen("documents", "Документы", "#/documents", "работа"));

        if (role == Role.HR || role == Role.ADMIN || role == Role.EXEC) {
            screens.add(new Screen("skills", "ИИ-скиллы", "#/skills", "система"));
        }
        if (role == Role.ADMIN) {
            screens.add(new Screen("admin", "Администрирование", "#/admin", "система"));
            screens.add(new Screen("design", "Дизайн-система", "#/design", "система"));
        }
        return screens;
    }
}
