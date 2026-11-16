package com.staffsense.demo.api.risk;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт администрирования прогнозов и CSRF-ручки.
 *
 * <p>Проверяем то, на что опирается новый клиент: журнал отдаётся администратору
 * и HR, но не руководителю проекта; CSRF-токен выдаётся в виде, пригодном для
 * заголовка POST-запроса.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RiskAdminApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail("admin.test@test.local").orElseGet(
                () -> userRepository.save(user("admin.test@test.local", "Админ", Role.ADMIN)));
        userRepository.findByEmail("pm.test@test.local").orElseGet(
                () -> userRepository.save(user("pm.test@test.local", "Пэ Мэ", Role.PM)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Журнал отдаётся администратору вместе с покрытием")
    void journalAvailableForAdmin() throws Exception {
        authenticate("admin.test@test.local", "ROLE_ADMIN");

        mockMvc.perform(get("/api/v1/risk/admin/journal").param("period", "QUARTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("QUARTER"))
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.latest").isArray())
                .andExpect(jsonPath("$.coverage.totalSnapshots").exists());
    }

    @Test
    @DisplayName("Руководитель проекта журнал не видит")
    void journalForbiddenForPm() throws Exception {
        authenticate("pm.test@test.local", "ROLE_PM");

        mockMvc.perform(get("/api/v1/risk/admin/journal"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CSRF-ручка отдаёт имя заголовка и токен")
    void csrfEndpoint() throws Exception {
        authenticate("admin.test@test.local", "ROLE_ADMIN");

        mockMvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    private void authenticate(String email, String authority) {
        var token = new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private User user(String email, String fullName, Role role) {
        return User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash("hash")
                .role(role)
                .department("HR")
                .position("Specialist")
                .salary(new BigDecimal("90000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2023, 1, 1))
                .active(true)
                .build();
    }
}
