package com.staffsense.demo.api.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.KpiRecord;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.model.WorkloadRecord;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.repository.kpi.KpiRecordRepository;
import com.staffsense.demo.repository.workload.WorkloadRecordRepository;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт {@code /api/v1/analytics}.
 *
 * <p>Проверяем три обещания, на которые опирается клиент:
 * <ol>
 *   <li>рядовой сотрудник в аналитику не проходит (403), HR — проходит;</li>
 *   <li>в ответе есть поля, из которых строится таблица, включая период —
 *       он у каждого сотрудника свой;</li>
 *   <li>отсутствие расчёта отдаётся как {@code null}/{@code hasData=false},
 *       а не как выдуманный ноль (прежние HTML-страницы подставляли 0.0).</li>
 * </ol>
 *
 * <p>Данные ищем по имени, а не по индексу в массиве: контекст Spring в тестах
 * общий на весь прогон, и {@code DataInitializer} мог наполнить H2 демо-набором.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsApiControllerTest {

    /** Уникальный отдел: агрегат по нему не смешивается с демо-данными. */
    private static final String TEST_DEPARTMENT = "Тестовая аналитика";

    private static final String HR_EMAIL = "analytics.hr@test.local";
    private static final String EMPLOYEE_EMAIL = "analytics.emp@test.local";

    private static final String LOADED_NAME = "Алиса Перегруженная";
    private static final String CALM_NAME = "Борис Спокойный";
    private static final String NO_DATA_NAME = "Виктор Без Данных";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkloadRecordRepository workloadRecordRepository;

    @Autowired
    private KpiRecordRepository kpiRecordRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User loaded;
    private User calm;
    private User noData;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail(HR_EMAIL).orElseGet(
                () -> userRepository.save(user(HR_EMAIL, "HR Аналитики", Role.HR, "HR")));
        userRepository.findByEmail(EMPLOYEE_EMAIL).orElseGet(
                () -> userRepository.save(user(EMPLOYEE_EMAIL, "Сотрудник Аналитики",
                        Role.EMPLOYEE, "HR")));

        loaded = userRepository.findByEmail("analytics.loaded@test.local").orElseGet(
                () -> userRepository.save(user("analytics.loaded@test.local", LOADED_NAME,
                        Role.EMPLOYEE, TEST_DEPARTMENT)));
        calm = userRepository.findByEmail("analytics.calm@test.local").orElseGet(
                () -> userRepository.save(user("analytics.calm@test.local", CALM_NAME,
                        Role.EMPLOYEE, TEST_DEPARTMENT)));
        noData = userRepository.findByEmail("analytics.nodata@test.local").orElseGet(
                () -> userRepository.save(user("analytics.nodata@test.local", NO_DATA_NAME,
                        Role.EMPLOYEE, TEST_DEPARTMENT)));

        LocalDate periodStart = LocalDate.now().withDayOfMonth(1);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());

        if (workloadRecordRepository.findByUserIdOrderByPeriodEndDesc(loaded.getId()).isEmpty()) {
            workloadRecordRepository.save(WorkloadRecord.builder()
                    .user(loaded)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .totalEstimatedHours(232.0)
                    .capacityHours(160.0)
                    .workloadIndex(1.45)
                    .overtimeHours(12.0)
                    .computedAt(LocalDateTime.now())
                    .build());
            workloadRecordRepository.save(WorkloadRecord.builder()
                    .user(calm)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .totalEstimatedHours(88.0)
                    .capacityHours(160.0)
                    .workloadIndex(0.55)
                    .overtimeHours(0.0)
                    .computedAt(LocalDateTime.now())
                    .build());
        }

        if (kpiRecordRepository.findByUserIdOrderByPeriodEndDesc(loaded.getId()).isEmpty()) {
            kpiRecordRepository.save(KpiRecord.builder()
                    .user(loaded)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .onTimeCompletionRate(0.9)
                    .reopenRate(0.1)
                    .managerRating(4.0)
                    .kpiScore(88.5)
                    .computedAt(LocalDateTime.now())
                    .build());
            kpiRecordRepository.save(KpiRecord.builder()
                    .user(calm)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .onTimeCompletionRate(0.7)
                    .reopenRate(0.2)
                    .kpiScore(62.0)
                    .computedAt(LocalDateTime.now())
                    .build());
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Нагрузка ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HR получает нагрузку с периодом, часами и признаком перегруза")
    void workloadContractForHr() throws Exception {
        JsonNode body = getJson("/api/v1/analytics/workload", HR_EMAIL, "ROLE_HR");

        assertEquals(1.3, body.path("overloadThreshold").asDouble(), 0.0001,
                "порог перегруза BR-002 должен приходить из сервиса");
        assertTrue(body.path("employees").path("content").isArray());
        assertTrue(body.path("generatedAt").isTextual());
        assertTrue(body.path("withDataCount").asInt() >= 2);

        JsonNode row = rowByName(body, "employees", LOADED_NAME);
        assertTrue(row.path("hasData").asBoolean());
        assertEquals(1.45, row.path("workloadIndex").asDouble(), 0.0001);
        assertTrue(row.path("overloaded").asBoolean(),
                "индекс 1.45 выше порога 1.3 — строка должна быть помечена перегрузом");
        assertEquals(232.0, row.path("totalEstimatedHours").asDouble(), 0.0001);
        assertEquals(160.0, row.path("capacityHours").asDouble(), 0.0001);
        assertEquals(12.0, row.path("overtimeHours").asDouble(), 0.0001);
        assertEquals(TEST_DEPARTMENT, row.path("department").asText());
        assertEquals(LocalDate.now().withDayOfMonth(1).toString(),
                row.path("periodStart").asText(), "период отдаётся в записи, а не угадывается");

        JsonNode page = body.path("employees");
        assertEquals(0, page.path("page").asInt());
        assertEquals(200, page.path("size").asInt());
        assertTrue(page.path("totalElements").asInt() >= 3);
    }

    @Test
    @DisplayName("Нагрузка по отделу считается только по сотрудникам с данными")
    void workloadTeamsAggregated() throws Exception {
        JsonNode body = getJson("/api/v1/analytics/workload", HR_EMAIL, "ROLE_HR");

        JsonNode team = teamByDepartment(body, TEST_DEPARTMENT);
        assertNotNull(team, "в ответе должен быть агрегат по отделу");
        assertEquals(3, team.path("employeeCount").asInt(),
                "в отделе три тестовых сотрудника");
        assertEquals(2, team.path("withDataCount").asInt(),
                "записи нагрузки есть только у двоих");
        assertEquals(1.0, team.path("avgWorkloadIndex").asDouble(), 0.0001,
                "(1.45 + 0.55) / 2 = 1.0 по данным, а не по трём строкам");
        assertEquals(1, team.path("overloadedCount").asInt());
    }

    // ── KPI ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HR получает KPI с его слагаемыми и периодом")
    void kpiContractForHr() throws Exception {
        JsonNode body = getJson("/api/v1/analytics/kpi", HR_EMAIL, "ROLE_HR");

        assertTrue(body.path("employees").path("content").isArray());
        assertTrue(body.path("generatedAt").isTextual());
        assertTrue(body.path("withDataCount").asInt() >= 2);

        JsonNode row = rowByName(body, "employees", LOADED_NAME);
        assertTrue(row.path("hasData").asBoolean());
        assertEquals(88.5, row.path("kpiScore").asDouble(), 0.0001);
        assertEquals(0.9, row.path("onTimeCompletionRate").asDouble(), 0.0001);
        assertEquals(0.1, row.path("reopenRate").asDouble(), 0.0001);
        assertEquals(4.0, row.path("managerRating").asDouble(), 0.0001);
        assertEquals(LocalDate.now().withDayOfMonth(1).toString(),
                row.path("periodStart").asText());
    }

    @Test
    @DisplayName("Сотрудник без расчёта получает null, а не ноль")
    void missingDataIsNullNotZero() throws Exception {
        JsonNode workload = getJson("/api/v1/analytics/workload", HR_EMAIL, "ROLE_HR");
        JsonNode workloadRow = rowByName(workload, "employees", NO_DATA_NAME);
        assertFalse(workloadRow.path("hasData").asBoolean());
        assertTrue(workloadRow.path("workloadIndex").isNull(),
                "ноль означал бы измеренную нулевую нагрузку — это ложь");
        assertTrue(workloadRow.path("periodStart").isNull());
        assertFalse(workloadRow.path("overloaded").asBoolean());

        JsonNode kpi = getJson("/api/v1/analytics/kpi", HR_EMAIL, "ROLE_HR");
        JsonNode kpiRow = rowByName(kpi, "employees", NO_DATA_NAME);
        assertFalse(kpiRow.path("hasData").asBoolean());
        assertTrue(kpiRow.path("kpiScore").isNull());
    }

    // ── Сводка ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Сводка отдаёт счётчики и средние")
    void summaryContract() throws Exception {
        JsonNode body = getJson("/api/v1/analytics/summary", HR_EMAIL, "ROLE_HR");

        assertTrue(body.path("employees").asInt() >= 3);
        assertTrue(body.path("withWorkload").asInt() >= 2);
        assertTrue(body.path("withKpi").asInt() >= 2);
        assertTrue(body.path("overloaded").asInt() >= 1);
        assertTrue(body.path("avgWorkloadIndex").isNumber());
        assertTrue(body.path("avgKpiScore").isNumber());
        assertTrue(body.path("generatedAt").isTextual());
    }

    // ── Доступ ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Рядовой сотрудник получает 403 на всех ручках аналитики")
    void employeeIsForbidden() throws Exception {
        authenticate(EMPLOYEE_EMAIL, "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/analytics/workload")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/analytics/kpi")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/analytics/summary")).andExpect(status().isForbidden());
    }

    // ── Вспомогательное ──────────────────────────────────────────────────

    private JsonNode getJson(String path, String email, String authority) throws Exception {
        authenticate(email, authority);
        MvcResult result = mockMvc.perform(get(path).param("size", "200"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode rowByName(JsonNode body, String collection, String fullName) {
        for (JsonNode row : body.path(collection).path("content")) {
            if (fullName.equals(row.path("fullName").asText())) {
                return row;
            }
        }
        throw new AssertionError("В ответе нет строки сотрудника: " + fullName);
    }

    private JsonNode teamByDepartment(JsonNode body, String department) {
        for (JsonNode team : body.path("teams")) {
            if (department.equals(team.path("department").asText())) {
                return team;
            }
        }
        return null;
    }

    private void authenticate(String email, String authority) {
        SecurityContextHolder.clearContext();
        var token = new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private User user(String email, String fullName, Role role, String department) {
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
                .hireDate(LocalDate.of(2023, 3, 1))
                .active(true)
                .build();
    }
}
