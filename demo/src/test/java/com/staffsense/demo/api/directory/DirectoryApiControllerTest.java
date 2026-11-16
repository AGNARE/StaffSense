package com.staffsense.demo.api.directory;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт справочника сотрудников (FR-043, BR-031).
 *
 * <p>Главное обещание справочника — он открыт любому сотруднику, поэтому
 * в нём нет ни оклада, ни KPI, ни риска. Тест проверяет это по всему телу
 * ответа: сотрудник с «заметным» окладом заведён специально, и если оклад
 * попадёт в JSON — проверка упадёт.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DirectoryApiControllerTest {

    /** Отдел, которого нет у остальных тестовых данных: счётчики предсказуемы. */
    private static final String DEPARTMENT = "Справочник-тест";

    /** Заметный оклад: если он утечёт в ответ, проверка на подстроку это поймает. */
    private static final String SECRET_SALARY = "987654.32";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User manager = userRepository.findByEmail("dir.manager@test.local").orElseGet(
                () -> userRepository.save(user("dir.manager@test.local",
                        "Руководитель Справочника", Role.PM, "Руководитель отдела", null)));

        userRepository.findByEmail("dir.emp1@test.local").orElseGet(
                () -> userRepository.save(user("dir.emp1@test.local",
                        "Сотрудник Справочника Первый", Role.EMPLOYEE, "Java Developer", manager)));
        userRepository.findByEmail("dir.emp2@test.local").orElseGet(
                () -> userRepository.save(user("dir.emp2@test.local",
                        "Сотрудник Справочника Второй", Role.EMPLOYEE, "Frontend Developer", manager)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Справочник отдаёт контакты и структуру, но не оклады")
    void directoryHasNoSalary() throws Exception {
        authenticate("dir.emp1@test.local", "ROLE_EMPLOYEE");

        MvcResult result = mockMvc.perform(get("/api/v1/directory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.employees[*].fullName")
                        .value(hasItem("Сотрудник Справочника Первый")))
                .andExpect(jsonPath("$.employees[*].email").value(hasItem("dir.emp1@test.local")))
                // у подразделения из теста ровно три активных сотрудника
                .andExpect(jsonPath("$.departments[?(@.name == '" + DEPARTMENT + "')].count")
                        .value(hasItem(3)))
                .andExpect(jsonPath("$.managers[?(@.fullName == 'Руководитель Справочника')].directReports")
                        .value(hasItem(2)))
                .andReturn();

        String json = result.getResponse().getContentAsString();

        // 🔴 BR-031: оклада в контракте нет — ни поля, ни значения.
        assertFalse(json.contains("salary"), "в справочнике не должно быть поля salary");
        assertFalse(json.contains(SECRET_SALARY), "значение оклада не должно попадать в справочник");
        assertFalse(json.contains("987654"), "даже усечённое значение оклада не должно утекать");

        // Ожидаемые безопасные поля на месте.
        assertTrue(json.contains("managerName"), "руководитель — часть безопасного набора");
        assertTrue(json.contains("department"));
    }

    @Test
    @DisplayName("Поиск и фильтр по подразделению сужают список")
    void searchAndDepartmentFilter() throws Exception {
        authenticate("dir.emp1@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/directory").param("q", "Второй"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shown").value(1))
                .andExpect(jsonPath("$.employees[0].fullName").value("Сотрудник Справочника Второй"));

        mockMvc.perform(get("/api/v1/directory").param("department", DEPARTMENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shown").value(3));
    }

    @Test
    @DisplayName("Справочник закрыт от анонимного запроса — 401")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/directory"))
                .andExpect(status().isUnauthorized());
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private void authenticate(String email, String authority) {
        SecurityContextHolder.clearContext();
        var token = new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private User user(String email, String fullName, Role role, String position, User manager) {
        return User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash("hash")
                .role(role)
                .department(DEPARTMENT)
                .position(position)
                .manager(manager)
                .salary(new BigDecimal(SECRET_SALARY))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2023, 3, 1))
                .active(true)
                .build();
    }
}
