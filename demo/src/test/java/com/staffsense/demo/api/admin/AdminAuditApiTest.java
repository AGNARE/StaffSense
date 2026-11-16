package com.staffsense.demo.api.admin;

import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.AdminAuditRepository;
import com.staffsense.demo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Журнал действий администратора.
 *
 * <p>Проверяем: действие попадает в журнал с актором и целью, а читать журнал
 * может только администратор.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuditApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminAuditRepository auditRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final java.util.List<Long> createdIds = new java.util.ArrayList<>();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Сброс пароля оставляет запись с актором и целью")
    void passwordResetIsRecorded() throws Exception {
        User target = ensure("audit.target@test.local", "Аудит Цель", Role.EMPLOYEE);
        authenticate("audit.admin@test.local", "ROLE_ADMIN", Role.ADMIN);
        long before = auditRepository.count();

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/reset-password")
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(auditRepository.count()).isEqualTo(before + 1);
        assertThat(auditRepository.findAllByOrderByCreatedAtDesc(
                        org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().get(0).getAction()).isEqualTo("PASSWORD_RESET");
    }

    @Test
    @DisplayName("Смена роли оставляет запись")
    void roleChangeIsRecorded() throws Exception {
        User target = ensure("audit.role@test.local", "Аудит Роль", Role.EMPLOYEE);
        authenticate("audit.admin2@test.local", "ROLE_ADMIN", Role.ADMIN);

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/role")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PM\"}"))
                .andExpect(status().isOk());

        assertThat(auditRepository.findAllByOrderByCreatedAtDesc(
                        org.springframework.data.domain.PageRequest.of(0, 5))
                .getContent())
                .anyMatch(e -> "ROLE_CHANGED".equals(e.getAction())
                        && target.getEmail().equals(e.getTargetEmail()));
    }

    @Test
    @DisplayName("Журнал читает администратор")
    void auditReadableByAdmin() throws Exception {
        authenticate("audit.admin3@test.local", "ROLE_ADMIN", Role.ADMIN);

        mockMvc.perform(get("/api/v1/admin/audit")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HR журнал не читает")
    void auditClosedForHr() throws Exception {
        authenticate("audit.hr@test.local", "ROLE_HR", Role.HR);

        mockMvc.perform(get("/api/v1/admin/audit")).andExpect(status().isForbidden());
    }

    private void authenticate(String email, String authority, Role role) {
        userRepository.findByEmail(email).orElseGet(() -> record(userRepository.save(build(email, email, role))));
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a",
                        List.of(new SimpleGrantedAuthority(authority))));
    }

    private User ensure(String email, String name, Role role) {
        return userRepository.findByEmail(email).orElseGet(() -> record(userRepository.save(build(email, name, role))));
    }

    private User build(String email, String name, Role role) {
        return User.builder()
                .fullName(name)
                .email(email)
                .passwordHash(passwordEncoder.encode("initial-password"))
                .role(role)
                .department("HR")
                .position("Specialist")
                .salary(new BigDecimal("70000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2024, 3, 1))
                .active(true)
                .build();
    }
    /** Запоминает созданного пользователя, чтобы удалить после теста. */
    private User record(User user) {
        createdIds.add(user.getId());
        return user;
    }
}
