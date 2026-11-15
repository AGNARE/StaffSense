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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Аудит не пишет лишнего.
 *
 * <p>Отдельный класс, потому что проверяет обратное утверждение: если действие
 * ничего не изменило (роль та же, статус тот же), записи быть не должно.
 * Именно на этом я ошибся руками: строка аудита стояла в ветке «изменений нет»,
 * и журнал оставался пустым при реальных изменениях.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuditNoopTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminAuditRepository auditRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Повторная установка той же роли не создаёт запись")
    void noAuditWhenRoleUnchanged() throws Exception {
        User target = ensure("noop.role@test.local", Role.EMPLOYEE);
        authenticate("noop.admin@test.local");
        long before = auditRepository.count();

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/role")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isOk());

        assertThat(auditRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("Повторная установка того же статуса не создаёт запись")
    void noAuditWhenActiveUnchanged() throws Exception {
        User target = ensure("noop.active@test.local", Role.EMPLOYEE);
        authenticate("noop.admin2@test.local");
        long before = auditRepository.count();

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/active")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk());

        assertThat(auditRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("Реальное изменение статуса запись создаёт")
    void auditWhenActiveChanged() throws Exception {
        User target = ensure("noop.change@test.local", Role.EMPLOYEE);
        authenticate("noop.admin3@test.local");
        long before = auditRepository.count();

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/active")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        assertThat(auditRepository.count()).isEqualTo(before + 1);
    }

    private void authenticate(String email) {
        userRepository.findByEmail(email).orElseGet(() -> userRepository.save(build(email, Role.ADMIN)));
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private User ensure(String email, Role role) {
        return userRepository.findByEmail(email).orElseGet(() -> userRepository.save(build(email, role)));
    }

    private User build(String email, Role role) {
        return User.builder()
                .fullName("Нулевой Аудит " + email)
                .email(email)
                .passwordHash(passwordEncoder.encode("initial-password"))
                .role(role)
                .department("HR")
                .position("Specialist")
                .salary(new BigDecimal("70000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2024, 4, 1))
                .active(true)
                .build();
    }
}
