package com.staffsense.demo.api.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.hr.VacationBalanceService;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * «Мой остаток отпуска» — контракт {@code GET /api/v1/hr/vacations/me}.
 *
 * <p>Ручка уже существовала, но её поведение для рядового сотрудника нигде не
 * проверялось: список {@code /api/v1/hr/vacations} закрыт управленческими
 * ролями, и без отдельного теста легко не заметить, что {@code /me} перестал
 * быть доступен сотруднику. Экран «Мой день» строится на этой ручке, поэтому
 * фиксируем три обещания: сотрудник получает свой баланс, «не начислено» не
 * превращается в ноль, анонимный запрос получает 401, а не страницу логина.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VacationMeApiTest {

    private static final int YEAR = 2026;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VacationBalanceService balanceService;

    @Autowired
    private ObjectMapper objectMapper;

    private User employee;

    @BeforeEach
    void setUp() {
        employee = userRepository.findByEmail("vacme.emp@test.local").orElseGet(
                () -> userRepository.save(User.builder()
                        .fullName("Сотрудник Отпуска")
                        .email("vacme.emp@test.local")
                        .passwordHash("hash")
                        .role(Role.EMPLOYEE)
                        .department("Engineering")
                        .position("Developer")
                        .salary(new BigDecimal("100000"))
                        .gradeLevel(GradeLevel.MIDDLE)
                        .weeklyCapacityHours(40)
                        .hireDate(LocalDate.of(2024, 2, 1))
                        .active(true)
                        .build()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Сотрудник видит свой остаток отпуска")
    void employeeSeesOwnBalance() throws Exception {
        balanceService.grant(employee, YEAR, new BigDecimal("28"), new BigDecimal("4"), "политика 2026");
        authenticate(employee.getEmail(), "ROLE_EMPLOYEE");

        JsonNode body = read(mockMvc.perform(get("/api/v1/hr/vacations/me?year=" + YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(employee.getId().intValue()))
                .andExpect(jsonPath("$.fullName").value("Сотрудник Отпуска"))
                .andExpect(jsonPath("$.year").value(YEAR))
                .andExpect(jsonPath("$.entitledKnown").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        // Суммы сравниваем как числа: 28.0 и 28 — одно и то же значение.
        assertThat(body.get("entitledDays").decimalValue()).isEqualByComparingTo("28");
        assertThat(body.get("carriedOverDays").decimalValue()).isEqualByComparingTo("4");
        assertThat(body.get("usedDays").decimalValue()).isEqualByComparingTo("0");
        assertThat(body.get("remainingDays").decimalValue()).isEqualByComparingTo("32");
    }

    @Test
    @DisplayName("Без начисления остаток не подставляется нулём")
    void noEntitlementIsNotZero() throws Exception {
        authenticate(employee.getEmail(), "ROLE_EMPLOYEE");

        JsonNode body = read(mockMvc.perform(get("/api/v1/hr/vacations/me?year=2031"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entitledKnown").value(false))
                .andExpect(jsonPath("$.entitledDays").doesNotExist())
                .andExpect(jsonPath("$.remainingDays").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(body.get("entitledDays").isNull()).isTrue();
        assertThat(body.get("remainingDays").isNull()).isTrue();
    }

    @Test
    @DisplayName("Без параметра года отдаётся текущий год")
    void defaultsToCurrentYear() throws Exception {
        authenticate(employee.getEmail(), "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/hr/vacations/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(LocalDate.now().getYear()));
    }

    @Test
    @DisplayName("Анонимный запрос получает 401, а не страницу логина")
    void anonymousGetsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/hr/vacations/me"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode read(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private void authenticate(String email, String authority) {
        SecurityContextHolder.clearContext();
        var token = new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
