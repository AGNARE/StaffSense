package com.staffsense.demo.api.employee;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт {@code /api/v1/employees}.
 *
 * <p>Проверяем то, ради чего ресурс появился: каталог отдаёт данные, поиск и
 * фильтр работают на сервере, PM видит только свою команду, а карточка
 * недоступна чужим сотрудникам.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User manager;
    private User inTeam;
    private User outOfTeam;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail("emp.hr@test.local").orElseGet(
                () -> userRepository.save(user("emp.hr@test.local", "HR Admin", Role.HR,
                        "HR", null)));

        manager = userRepository.findByEmail("emp.pm@test.local").orElseGet(
                () -> userRepository.save(user("emp.pm@test.local", "Пэ Мэ", Role.PM,
                        "Product", null)));

        inTeam = userRepository.findByEmail("emp.in@test.local").orElseGet(
                () -> userRepository.save(user("emp.in@test.local", "Своя Команда",
                        Role.EMPLOYEE, "Engineering", manager)));
        outOfTeam = userRepository.findByEmail("emp.out@test.local").orElseGet(
                () -> userRepository.save(user("emp.out@test.local", "Чужая Команда",
                        Role.EMPLOYEE, "Sales", null)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Каталог отдаёт страницу с полями контракта")
    void listReturnsEmployees() throws Exception {
        authenticate("emp.hr@test.local", "ROLE_HR");

        mockMvc.perform(get("/api/v1/employees").param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.content[*].fullName").value(hasItem("Своя Команда")))
                .andExpect(jsonPath("$.content[*].fullName").value(hasItem("Чужая Команда")));
    }

    @Test
    @DisplayName("Поиск фильтрует по имени на сервере")
    void searchFiltersByName() throws Exception {
        authenticate("emp.hr@test.local", "ROLE_HR");

        mockMvc.perform(get("/api/v1/employees")
                        .param("search", "Чужая")
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].fullName").value(hasItem("Чужая Команда")))
                .andExpect(jsonPath("$.content[*].fullName").value(not(hasItem("Своя Команда"))));
    }

    @Test
    @DisplayName("Фильтр по отделу возвращает только этот отдел")
    void filtersByDepartment() throws Exception {
        authenticate("emp.hr@test.local", "ROLE_HR");

        mockMvc.perform(get("/api/v1/employees")
                        .param("department", "Sales")
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].department").value(hasItem("Sales")))
                .andExpect(jsonPath("$.content[*].department").value(not(hasItem("Engineering"))));
    }

    @Test
    @DisplayName("PM видит только свою команду")
    void pmSeesOwnTeamOnly() throws Exception {
        authenticate("emp.pm@test.local", "ROLE_PM");

        mockMvc.perform(get("/api/v1/employees").param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].fullName").value(hasItem("Своя Команда")))
                .andExpect(jsonPath("$.content[*].fullName").value(not(hasItem("Чужая Команда"))));
    }

    @Test
    @DisplayName("PM не открывает карточку сотрудника вне команды")
    void pmCannotOpenForeignProfile() throws Exception {
        authenticate("emp.pm@test.local", "ROLE_PM");

        mockMvc.perform(get("/api/v1/employees/" + outOfTeam.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Карточка сотрудника отдаёт профиль")
    void detailReturnsProfile() throws Exception {
        authenticate("emp.hr@test.local", "ROLE_HR");

        mockMvc.perform(get("/api/v1/employees/" + inTeam.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Своя Команда"))
                .andExpect(jsonPath("$.department").value("Engineering"))
                .andExpect(jsonPath("$.managerName").value("Пэ Мэ"));
    }

    @Test
    @DisplayName("Рядовой сотрудник в каталог не проходит")
    void employeeIsForbidden() throws Exception {
        authenticate("emp.in@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/employees"))
                .andExpect(status().isForbidden());
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private void authenticate(String email, String authority) {
        var token = new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private User user(String email, String fullName, Role role, String department, User boss) {
        return User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash("hash")
                .role(role)
                .department(department)
                .position("Specialist")
                .salary(new BigDecimal("90000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2023, 4, 1))
                .active(true)
                .manager(boss)
                .build();
    }
}
