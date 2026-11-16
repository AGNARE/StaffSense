package com.staffsense.demo.api.me;

import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Инвариант: меню обещает только то, что действительно открывается.
 *
 * <p>Проверяем согласованность двух источников: {@link RoleScreens} (что роль
 * видит в навигации) и фактические правила доступа (что реально отвечает 200).
 * Если экран есть в меню, его API должно открываться; если экрана нет — либо
 * API закрыт, либо экран доступен всем и в меню ему не место.
 *
 * <p>Именно этот класс ошибок ловился руками: маршрут есть, а сервер отвечает
 * 403 — или наоборот, меню пустое, хотя данные доступны.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleMenuConsistencyTest {

    /** Экран каталога → его API. Роуты, у которых нет отдельного API, опущены. */
    private static final Map<String, String> SCREEN_TO_API = Map.of(
            "risk", "/api/v1/risk?size=1",
            "employees", "/api/v1/employees?size=1",
            "analytics", "/api/v1/analytics/summary",
            "admin-users", "/api/v1/admin/users?size=1",
            "documents", "/api/v1/documents",
            "memos", "/api/v1/memos",
            "surveys", "/api/v1/surveys",
            "skills", "/api/v1/skills"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("Экран из меню открывается, отсутствующий — закрыт или общий")
    void menuMatchesAccess(Role role) throws Exception {
        String email = "menu." + role.name().toLowerCase() + "@test.local";
        userRepository.findByEmail(email).orElseGet(() -> userRepository.save(build(email, role)));
        authenticate(email, role);

        List<String> screens = RoleScreens.forRole(role).stream()
                .map(RoleScreens.Screen::id)
                .toList();

        SCREEN_TO_API.forEach((screen, api) -> {
            int status = statusOf(api);
            boolean inMenu = screens.contains(screen);

            if (inMenu) {
                assertThat(status)
                        .as("роль %s: экран «%s» в меню, но %s отвечает %s", role, screen, api, status)
                        .isBetween(200, 299);
            } else {
                assertThat(status)
                        .as("роль %s: экрана «%s» нет в меню, а %s отвечает %s — меню скрывает доступное",
                                role, screen, api, status)
                        .isIn(401, 403);
            }
        });
    }

    private int statusOf(String api) {
        try {
            return mockMvc.perform(get(api)).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new IllegalStateException("запрос " + api + " не выполнился", e);
        }
    }

    private void authenticate(String email, Role role) {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private User build(String email, Role role) {
        return User.builder()
                .fullName("Проверка Меню " + role.name())
                .email(email)
                .passwordHash("hash")
                .role(role)
                .department("HR")
                .position("Specialist")
                .salary(new BigDecimal("70000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2024, 2, 1))
                .active(true)
                .build();
    }
}
