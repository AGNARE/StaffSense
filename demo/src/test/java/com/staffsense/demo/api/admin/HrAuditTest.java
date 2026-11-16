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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Аудит кадровых действий.
 *
 * <p>Журнал задумывался для администратора, но кадровые действия меняют
 * положение сотрудника не меньше. Проверяем, что начисление отпуска и
 * действия по найму оставляют запись.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HrAuditTest {

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
    @DisplayName("Начисление отпуска попадает в журнал")
    void vacationGrantRecorded() throws Exception {
        User employee = ensure("hr.audit.vac@test.local", Role.EMPLOYEE);
        ensure("hr.audit.hr@test.local", Role.HR);
        authenticate("hr.audit.hr@test.local");
        long before = auditRepository.count();

        mockMvc.perform(post("/api/v1/hr/vacations/" + employee.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"year\":2026,\"entitledDays\":28,\"carriedOverDays\":2}"))
                .andExpect(status().isOk());

        assertThat(auditRepository.count()).isEqualTo(before + 1);
        assertThat(auditRepository.findAllByOrderByCreatedAtDesc(
                        org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().get(0).getAction()).isEqualTo("VACATION_GRANTED");
    }

    @Test
    @DisplayName("Открытие вакансии и добавление кандидата попадают в журнал")
    void hiringRecorded() throws Exception {
        ensure("hr.audit.recruiter@test.local", Role.HR);
        authenticate("hr.audit.recruiter@test.local");
        long before = auditRepository.count();

        String response = mockMvc.perform(post("/api/v1/hiring/vacancies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Аудит Вакансия\",\"department\":\"Engineering\","
                                + "\"gradeLevel\":\"MIDDLE\",\"openings\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(auditRepository.count()).isEqualTo(before + 1);

        String vacancyId = response.replaceAll(".*\"id\":(\\d+).*", "$1");
        mockMvc.perform(post("/api/v1/hiring/vacancies/" + vacancyId + "/candidates")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Аудит Кандидат\",\"email\":\"hr.audit.cand@test.local\"}"))
                .andExpect(status().isOk());

        assertThat(auditRepository.count()).isEqualTo(before + 2);
        assertThat(auditRepository.findAllByOrderByCreatedAtDesc(
                        org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().get(0).getAction()).isEqualTo("CANDIDATE_ADDED");
    }

    private void authenticate(String email) {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_HR"))));
    }

    private User ensure(String email, Role role) {
        return userRepository.findByEmail(email).orElseGet(() -> userRepository.save(User.builder()
                .fullName("Аудит " + email)
                .email(email)
                .passwordHash(passwordEncoder.encode("initial-password"))
                .role(role)
                .department("HR")
                .position("Specialist")
                .salary(new BigDecimal("70000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2024, 6, 1))
                .active(true)
                .build()));
    }
}
