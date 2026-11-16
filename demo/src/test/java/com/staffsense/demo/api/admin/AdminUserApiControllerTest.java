package com.staffsense.demo.api.admin;

import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт администрирования пользователей.
 *
 * <p>Проверяем не «ручка отвечает 200», а правила, ради которых экран
 * существует: администратор видит учётные записи, HR — нет; себя заблокировать
 * нельзя; последнего администратора нельзя разжаловать; матрица доступа
 * содержит все пять ролей и не выдаёт сотруднику админских разделов.
 *
 * <p>Тест транзакционный: подготовка данных и POST-запросы откатываются,
 * поэтому прогон не оставляет мусора в базе и не зависит от предыдущих
 * прогонов.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminUserApiControllerTest {

    private static final String ADMIN_EMAIL = "admin.api@test.local";
    private static final String HR_EMAIL = "hr.api@test.local";
    private static final String TARGET_EMAIL = "admin.target@test.local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        ensureUser(ADMIN_EMAIL, "Админ API", Role.ADMIN);
        ensureUser(HR_EMAIL, "Эйч Ар API", Role.HR);
        ensureUser(TARGET_EMAIL, "Целевой Админ", Role.ADMIN);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Список ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("ADMIN получает список пользователей без парольного хеша")
    void adminSeesUsers() throws Exception {
        authenticate(ADMIN_EMAIL, "ROLE_ADMIN");

        mockMvc.perform(get("/api/v1/admin/users").param("search", "admin.api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.content[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].userId").exists())
                // пароль и кадровые атрибуты в контракт не попадают
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].salary").doesNotExist())
                .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    @DisplayName("Фильтр по роли сужает выдачу")
    void roleFilter() throws Exception {
        authenticate(ADMIN_EMAIL, "ROLE_ADMIN");

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("role", "HR")
                        .param("search", "hr.api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value(HR_EMAIL))
                .andExpect(jsonPath("$.content[0].role").value("HR"));
    }

    @Test
    @DisplayName("HR список пользователей не видит")
    void hrForbidden() throws Exception {
        authenticate(HR_EMAIL, "ROLE_HR");

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    // ── Создание ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Создание возвращает временный пароль один раз")
    void createUserReturnsTemporaryPassword() throws Exception {
        authenticate(ADMIN_EMAIL, "ROLE_ADMIN");
        String email = "new.admin.api@" + System.nanoTime() + ".test.local";

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Новый Сотрудник",
                                  "email": "%s",
                                  "role": "HR",
                                  "department": "HR",
                                  "position": "Рекрутер"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("HR"))
                .andExpect(jsonPath("$.user.active").value(true))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("Повторный email — конфликт, а не второй аккаунт")
    void duplicateEmailRejected() throws Exception {
        authenticate(ADMIN_EMAIL, "ROLE_ADMIN");

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Дубль",
                                  "email": "%s",
                                  "role": "EMPLOYEE",
                                  "department": "HR",
                                  "position": "Специалист"
                                }
                                """.formatted(ADMIN_EMAIL.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("уже существует")));
    }

    // ── Смена роли ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Разжаловать администратора можно, если он не последний")
    void demoteAdminWhenAnotherExists() throws Exception {
        authenticate(ADMIN_EMAIL, "ROLE_ADMIN");
        Long targetId = userId(TARGET_EMAIL);

        mockMvc.perform(post("/api/v1/admin/users/" + targetId + "/role")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(targetId))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    @DisplayName("Последнего активного администратора разжаловать нельзя")
    void cannotDemoteLastAdmin() throws Exception {
        authenticate(ADMIN_EMAIL, "ROLE_ADMIN");
        Long targetId = userId(TARGET_EMAIL);

        // Оставляем ровно одного активного администратора — целевого.
        // Изменения откатятся вместе с тестовой транзакцией.
        for (User admin : userRepository.findByRole(Role.ADMIN)) {
            if (!admin.getId().equals(targetId)) {
                admin.setActive(false);
                userRepository.save(admin);
            }
        }

        mockMvc.perform(post("/api/v1/admin/users/" + targetId + "/role")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("последнего активного администратора")));
    }

    // ── Блокировка ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Заблокировать самого себя нельзя")
    void cannotBlockSelf() throws Exception {
        authenticate(ADMIN_EMAIL, "ROLE_ADMIN");
        Long selfId = userId(ADMIN_EMAIL);

        mockMvc.perform(post("/api/v1/admin/users/" + selfId + "/active")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("самого себя")));
    }

    @Test
    @DisplayName("Блокировка и разблокировка другого пользователя работают")
    void blockAndUnblockAnotherUser() throws Exception {
        authenticate(ADMIN_EMAIL, "ROLE_ADMIN");
        Long targetId = userId(TARGET_EMAIL);

        mockMvc.perform(post("/api/v1/admin/users/" + targetId + "/active")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/v1/admin/users/" + targetId + "/active")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    // ── Матрица доступа ──────────────────────────────────────────────────

    @Test
    @DisplayName("Матрица содержит все пять ролей и не даёт сотруднику админских разделов")
    void accessMatrixHasAllRoles() throws Exception {
        authenticate(ADMIN_EMAIL, "ROLE_ADMIN");

        mockMvc.perform(get("/api/v1/admin/access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(5))
                .andExpect(jsonPath("$.roles[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.roles[0].screens", hasItem("admin")))
                .andExpect(jsonPath("$.roles[4].role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.roles[4].screens", not(hasItem("admin"))))
                .andExpect(jsonPath("$.roles[4].screens", not(hasItem("employees"))))
                // список разделов один на всех: клиент не собирает его сам
                .andExpect(jsonPath("$.screens[*].id", hasItem("admin")))
                .andExpect(jsonPath("$.screens[*].title", hasItem("Администрирование прогнозов")));
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private void authenticate(String email, String authority) {
        SecurityContextHolder.clearContext();
        var token = new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private Long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    /**
     * Готовит пользователя в нужном состоянии.
     *
     * <p>Не {@code orElseGet}: предыдущий прогон мог оставить другую роль,
     * поэтому состояние приводится к ожидаемому каждый раз. Всё это внутри
     * тестовой транзакции и откатывается.
     */
    private User ensureUser(String email, String fullName, Role role) {
        User user = userRepository.findByEmail(email).orElseGet(User::new);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setDepartment("HR");
        user.setPosition("Specialist");
        user.setSalary(new BigDecimal("90000"));
        user.setGradeLevel(GradeLevel.MIDDLE);
        user.setWeeklyCapacityHours(40);
        user.setHireDate(LocalDate.of(2023, 1, 1));
        user.setActive(true);
        return userRepository.save(user);
    }
}
