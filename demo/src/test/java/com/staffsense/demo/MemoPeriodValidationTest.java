package com.staffsense.demo;

import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.MemoType;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.ServiceMemo;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.ServiceMemoRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Кросс-валидация дат служебной записки.
 *
 * <p>Записка — это период отсутствия, поэтому дата окончания раньше даты начала
 * не имеет смысла: раньше такой запрос либо создавал запись с отрицательным
 * числом дней, либо падал на расчёте. Теперь правило проверяется на двух
 * уровнях: в DTO (400 до бизнес-логики) и в сервисе (для прямых вызовов).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MemoPeriodValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceMemoRepository memoRepository;

    private User author;

    @BeforeEach
    void setUp() {
        author = userRepository.findByEmail("memo.period@test.local").orElseGet(
                () -> userRepository.save(User.builder()
                        .fullName("Проверка Периода")
                        .email("memo.period@test.local")
                        .passwordHash("hash")
                        .role(Role.EMPLOYEE)
                        .department("Product")
                        .position("Specialist")
                        .salary(new BigDecimal("70000"))
                        .gradeLevel(GradeLevel.MIDDLE)
                        .weeklyCapacityHours(40)
                        .hireDate(LocalDate.of(2024, 3, 1))
                        .active(true)
                        .build()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        author.getEmail(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Дата окончания раньше даты начала — 400 и понятное сообщение")
    void invertedPeriodIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/memos")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"VACATION","startDate":"2026-12-10","endDate":"2026-12-03",
                                 "reason":"Перевёрнутый период"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Дата окончания раньше даты начала")));

        assertThat(memoRepository.findByUserIdOrderByCreatedAtDesc(author.getId()))
                .as("записка с перевёрнутым периодом не должна создаваться")
                .isEmpty();
    }

    @Test
    @DisplayName("Корректный период создаёт записку на визе руководителя")
    void correctPeriodIsCreated() throws Exception {
        mockMvc.perform(post("/api/v1/memos")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"VACATION","startDate":"2026-12-03","endDate":"2026-12-10",
                                 "reason":"Корректный период"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PM"))
                .andExpect(jsonPath("$.startDate").value("2026-12-03"))
                .andExpect(jsonPath("$.endDate").value("2026-12-10"))
                .andExpect(jsonPath("$.days").value(8));

        List<ServiceMemo> created = memoRepository.findByUserIdOrderByCreatedAtDesc(author.getId());
        assertThat(created).hasSize(1);
        assertThat(created.get(0).getMemoType()).isEqualTo(MemoType.VACATION);
    }

    @Test
    @DisplayName("Пустые даты отклоняются валидацией, а не 500")
    void missingDatesAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/memos")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"VACATION\"}"))
                .andExpect(status().isBadRequest());

        assertThat(memoRepository.findByUserIdOrderByCreatedAtDesc(author.getId())).isEmpty();
    }
}
