package com.staffsense.demo.api.me;

import com.staffsense.demo.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Навигация по ролям — цели каждой роли должны быть достижимы.
 *
 * <p>Проверяем не «сколько пунктов», а что у роли есть путь к её задачам
 * и нет доступа к чужим.
 */
class RoleScreensTest {

    private List<String> ids(Role role) {
        return RoleScreens.forRole(role).stream().map(RoleScreens.Screen::id).toList();
    }

    @Test
    @DisplayName("Сотрудник видит кабинет, «Мой день», «Мой путь», проекты, опросы, записки, портал и скиллы")
    void employeeGoals() {
        assertThat(ids(Role.EMPLOYEE))
                .containsExactly("me", "my-day", "journey", "onboarding", "surveys", "memos", "projects", "skills")
                .doesNotContain("risk", "admin", "employees", "analytics", "documents");
    }

    @Test
    @DisplayName("Руководитель видит команду, риски и проекты, но не админку")
    void pmGoals() {
        assertThat(ids(Role.PM))
                .contains("employees", "risk", "projects", "memos")
                .doesNotContain("admin", "design");
    }

    @Test
    @DisplayName("HR ведёт людей: кадры, документы, опросы, риски")
    void hrGoals() {
        assertThat(ids(Role.HR))
                .contains("employees", "documents", "surveys", "risk", "skills")
                .doesNotContain("admin");
    }

    @Test
    @DisplayName("Администратор видит всё, включая настройку системы")
    void adminGoals() {
        assertThat(ids(Role.ADMIN)).contains("admin", "design", "risk", "documents", "skills");
    }

    @Test
    @DisplayName("Каждая роль имеет доступ к своему кабинету")
    void everyRoleHasCabinet() {
        for (Role role : Role.values()) {
            assertThat(ids(role)).as("роль %s", role).contains("me");
        }
    }

    @Test
    @DisplayName("Маршруты уникальны внутри роли")
    void routesAreUnique() {
        for (Role role : Role.values()) {
            List<String> routes = RoleScreens.forRole(role).stream().map(RoleScreens.Screen::route).toList();
            assertThat(routes).as("роль %s", role).doesNotHaveDuplicates();
        }
    }
}
