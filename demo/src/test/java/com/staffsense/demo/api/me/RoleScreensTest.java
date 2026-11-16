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
                .containsExactly("me", "my-day", "surveys", "memos", "journey", "onboarding", "projects", "skills")
                .doesNotContain("risk", "admin", "employees", "analytics", "documents");
    }

    @Test
    @DisplayName("Руководитель видит команду, риски, проекты и сводку, но не админку")
    void pmGoals() {
        assertThat(ids(Role.PM))
                .contains("overview", "employees", "risk", "projects", "memos", "exec")
                .doesNotContain("admin", "admin-users", "design");
    }

    @Test
    @DisplayName("HR ведёт людей: кадры, документы, опросы, риски, сводку и пороги прогноза")
    void hrGoals() {
        assertThat(ids(Role.HR))
                .contains("overview", "employees", "documents", "surveys", "risk", "skills", "exec", "admin")
                .doesNotContain("admin-users", "design");
    }

    @Test
    @DisplayName("Администратор видит всё, включая настройку системы")
    void adminGoals() {
        assertThat(ids(Role.ADMIN))
                .contains("overview", "admin", "admin-users", "risk", "documents", "skills");
    }

    @Test
    @DisplayName("Обзор компании — раздел управленческих ролей, сотруднику он не показывается")
    void overviewIsManagementOnly() {
        assertThat(ids(Role.EMPLOYEE)).doesNotContain("overview");
        for (Role role : List.of(Role.ADMIN, Role.EXEC, Role.HR, Role.PM)) {
            assertThat(ids(role)).as("роль %s", role).contains("overview");
        }
    }

    @Test
    @DisplayName("Витрина дизайн-системы убрана из навигации всех ролей")
    void designScreenRemoved() {
        for (Role role : Role.values()) {
            assertThat(ids(role)).as("роль %s", role).doesNotContain("design");
        }
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
