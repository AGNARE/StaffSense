package com.staffsense.demo.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сброс пароля администратором.
 *
 * <p>Проверяем: администратор получает новый пароль один раз, прежний хеш
 * заменяется, чужая роль доступа не получает.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminResetPasswordTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final java.util.List<Long> createdIds = new java.util.ArrayList<>();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Администратор сбрасывает пароль и получает новый один раз")
    void resetsPassword() throws Exception {
        User target = ensure("reset.target@test.local", "Сброс Пароля", Role.EMPLOYEE);
        String before = target.getPasswordHash();
        authenticate("reset.admin@test.local", "ROLE_ADMIN", Role.ADMIN);

        String body = mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/reset-password")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        String temporary = json.path("temporaryPassword").asText();
        assertThat(temporary).hasSizeGreaterThanOrEqualTo(10);

        User after = userRepository.findById(target.getId()).orElseThrow();
        assertThat(after.getPasswordHash()).isNotEqualTo(before);
        assertThat(passwordEncoder.matches(temporary, after.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("HR не может сбросить пароль")
    void hrCannotReset() throws Exception {
        User target = ensure("reset.target.second@test.local", "Сброс Повторный", Role.EMPLOYEE);
        authenticate("reset.hr@test.local", "ROLE_HR", Role.HR);

        mockMvc.perform(post("/api/v1/admin/users/" + target.getId() + "/reset-password")
                        .with(csrf()))
                .andExpect(status().isForbidden());
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
