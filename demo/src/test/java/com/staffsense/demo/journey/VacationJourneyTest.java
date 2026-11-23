package com.staffsense.demo.journey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозной сценарий отпусков: право → баланс сотрудника → утверждённая записка.
 *
 * <p>Проверяем главное разделение модуля: <b>право</b> на отпуск — кадровое
 * решение (его начисляет HR), <b>использование</b> — факт из утверждённых
 * записок. Поэтому сценарий идёт целиком:
 * <ol>
 *   <li>HR начисляет право;</li>
 *   <li>сотрудник видит свой баланс через {@code /me} — ровно то, что начислили;</li>
 *   <li>сотрудник подаёт записку на отпуск, её визирует руководитель и
 *       утверждает HR — и остаток в {@code /me} уменьшается на дни отпуска,
 *       хотя отдельной записи об использовании никто не создавал;</li>
 * </ol>
 * и отдельно — «не начислено» не превращается в «ноль дней»: остаток
 * приходит {@code null}, а не {@code 0}.
 *
 * <p>Тест транзакционный: начисления, записки и отметки табеля (BR-030)
 * откатываются, база после прогона та же, что и до него.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VacationJourneyTest {

    private static final String HR_EMAIL = "journey.vacation.hr@test.local";
    private static final String PM_EMAIL = "journey.vacation.pm@test.local";
    private static final String EMPLOYEE_EMAIL = "journey.vacation.employee@test.local";
    private static final String UNGRANTED_EMAIL = "journey.vacation.ungranted@test.local";

    private static final int YEAR = 2026;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private User hr;
    private User pm;
    private User employee;
    private User ungranted;

    @BeforeEach
    void setUp() {
        hr = ensureUser(HR_EMAIL, "Кадровик Отпускных Путей", Role.HR, null);
        pm = ensureUser(PM_EMAIL, "Руководитель Отпускных Путей", Role.PM, null);
        employee = ensureUser(EMPLOYEE_EMAIL, "Сотрудник Отпускных Путей", Role.EMPLOYEE, pm);
        ungranted = ensureUser(UNGRANTED_EMAIL, "Сотрудник Без Права", Role.EMPLOYEE, pm);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    // ── Сценарий: начисление → баланс → утверждённая записка ─────────────

    @Test
    @DisplayName("HR начисляет право, сотрудник видит баланс, утверждённый отпуск уменьшает остаток")
    void grantedVacationAndApprovedMemoReduceRemaining() throws Exception {
        // 1. HR начисляет 24 дня права и 4 дня переноса.
        authenticate(HR_EMAIL, "ROLE_HR");

        mockMvc.perform(post("/api/v1/hr/vacations/" + employee.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"year":2026,"entitledDays":24,"carriedOverDays":4,
                                 "note":"Политика отпусков 2026"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(employee.getId()))
                .andExpect(jsonPath("$.year").value(YEAR))
                .andExpect(jsonPath("$.entitledDays").value(24))
                .andExpect(jsonPath("$.carriedOverDays").value(4))
                .andExpect(jsonPath("$.usedDays").value(0))
                .andExpect(jsonPath("$.remainingDays").value(28))
                .andExpect(jsonPath("$.entitledKnown").value(true));

        // 2. Сотрудник открывает свой баланс: видит те же 24 + 4 дня.
        authenticate(EMPLOYEE_EMAIL, "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/hr/vacations/me").param("year", String.valueOf(YEAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(employee.getId()))
                .andExpect(jsonPath("$.entitledDays").value(24))
                .andExpect(jsonPath("$.remainingDays").value(28))
                .andExpect(jsonPath("$.usedDays").value(0))
                .andExpect(jsonPath("$.entitledKnown").value(true));

        // 3. Сотрудник подаёт записку на отпуск: 3 календарных дня.
        MvcResult created = mockMvc.perform(post("/api/v1/memos")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"VACATION","startDate":"2026-05-11","endDate":"2026-05-13",
                                 "reason":"Отпуск по графику"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(employee.getId()))
                .andExpect(jsonPath("$.status").value("PENDING_PM"))
                .andExpect(jsonPath("$.days").value(3))
                .andReturn();

        long memoId = idOf(created);

        // 4. Виза руководителя: маршрут PENDING_PM → PENDING_HR.
        authenticate(PM_EMAIL, "ROLE_PM");
        mockMvc.perform(post("/api/v1/memos/" + memoId + "/approve-pm").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_HR"));

        // 5. Утверждение HR: PENDING_HR → APPROVED (+ дни уходят в табель, BR-030).
        authenticate(HR_EMAIL, "ROLE_HR");
        mockMvc.perform(post("/api/v1/memos/" + memoId + "/approve-hr").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.hrDecisionBy").value("Кадровик Отпускных Путей"));

        // 6. Последствие: использовано 3 дня, остаток 28 − 3 = 25.
        //    Отдельного поля «использовано» никто не записывал — оно считается
        //    из утверждённой записки.
        authenticate(EMPLOYEE_EMAIL, "ROLE_EMPLOYEE");
        mockMvc.perform(get("/api/v1/hr/vacations/me").param("year", String.valueOf(YEAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedDays").value(3))
                .andExpect(jsonPath("$.remainingDays").value(25))
                .andExpect(jsonPath("$.entitledDays").value(24))
                .andExpect(jsonPath("$.entitledKnown").value(true));

        // 7. То же видит HR в общем списке балансов — источник правды один.
        authenticate(HR_EMAIL, "ROLE_HR");
        mockMvc.perform(get("/api/v1/hr/vacations").param("year", String.valueOf(YEAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId==" + employee.getId() + ")].usedDays")
                        .value(org.hamcrest.Matchers.hasItem(3)))
                .andExpect(jsonPath("$[?(@.userId==" + employee.getId() + ")].remainingDays")
                        .value(org.hamcrest.Matchers.hasItem(25)));
    }

    // ── Сценарий: без начисления остаток неизвестен ──────────────────────

    @Test
    @DisplayName("Без начисления сотрудник видит остаток null, а не 0")
    void withoutEntitlementRemainingIsNull() throws Exception {
        authenticate(UNGRANTED_EMAIL, "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/hr/vacations/me").param("year", String.valueOf(YEAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ungranted.getId()))
                .andExpect(jsonPath("$.entitledDays").value(nullValue()))
                .andExpect(jsonPath("$.carriedOverDays").value(nullValue()))
                .andExpect(jsonPath("$.remainingDays").value(nullValue()))
                .andExpect(jsonPath("$.entitledKnown").value(false))
                // «использовано 0 дней» — правда, а «остаток 0» — ложь.
                .andExpect(jsonPath("$.usedDays").value(0));

        // HR видит то же самое в общем списке: «не начислено» не выдаётся за ноль.
        authenticate(HR_EMAIL, "ROLE_HR");
        mockMvc.perform(get("/api/v1/hr/vacations").param("year", String.valueOf(YEAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId==" + ungranted.getId() + ")].entitledKnown")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$[?(@.userId==" + ungranted.getId() + ")].remainingDays")
                        .value(org.hamcrest.Matchers.hasItem(nullValue())));
    }

    // ── Валидация тела начисления ────────────────────────────────────────

    /**
     * Отрицательное право отклоняется 400 и не доходит до записи.
     *
     * <p>Раньше {@code VacationApiController#grant} принимал
     * {@code @RequestBody GrantRequest} без {@code @Valid}, поэтому
     * {@code @DecimalMin("0")} не проверялся: {@code entitledDays: -5} давало
     * 200 и записывало отрицательное право. Сейчас стоит {@code @Valid},
     * ответ — 400, и данные остаются нетронутыми.
     */
    @Test
    void negativeEntitlementRejected() throws Exception {
        authenticate(HR_EMAIL, "ROLE_HR");

        mockMvc.perform(post("/api/v1/hr/vacations/" + employee.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"year":2026,"entitledDays":-5,"carriedOverDays":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Дней не может быть меньше нуля")));

        // Последствие: отказ не должен был создать право вовсе.
        authenticate(EMPLOYEE_EMAIL, "ROLE_EMPLOYEE");
        mockMvc.perform(get("/api/v1/hr/vacations/me").param("year", String.valueOf(YEAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entitledKnown").value(false))
                .andExpect(jsonPath("$.remainingDays").value(nullValue()));
    }

    /**
     * Пустое право — не «ноль дней»: клиент такую форму не отправляет, а ручка
     * обязана ответить 400 с понятным текстом, а не 409 от {@code NOT NULL}.
     */
    @Test
    @DisplayName("Начисление без поля entitledDays отклоняется 400, а не 409")
    void missingEntitlementRejectedWithBadRequest() throws Exception {
        authenticate(HR_EMAIL, "ROLE_HR");

        mockMvc.perform(post("/api/v1/hr/vacations/" + employee.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"year":2026,"carriedOverDays":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Укажите право на отпуск в днях")));
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private long idOf(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private void authenticate(String email, String authority) {
        var token = new UsernamePasswordAuthenticationToken(email, "n/a",
                List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(token);
        // MockMvc помнит контекст первого запроса теста, поэтому смена роли
        // посреди сценария обновляет и test-хранилище Security.
        TestSecurityContextHolder.setAuthentication(token);
    }

    private User ensureUser(String email, String fullName, Role role, User manager) {
        User user = userRepository.findByEmail(email).orElseGet(User::new);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setDepartment("Отдел отпускных путешествий");
        user.setPosition("Specialist");
        user.setSalary(new BigDecimal("85000"));
        user.setGradeLevel(GradeLevel.MIDDLE);
        user.setWeeklyCapacityHours(40);
        user.setHireDate(LocalDate.of(2023, 4, 3));
        user.setActive(true);
        user.setManager(manager);
        return userRepository.save(user);
    }
}
