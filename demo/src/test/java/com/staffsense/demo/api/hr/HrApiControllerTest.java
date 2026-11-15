package com.staffsense.demo.api.hr;

import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.TimesheetPeriodStatus;
import com.staffsense.demo.model.User;
import com.staffsense.demo.model.WorkSchedulePeriod;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.repository.WorkSchedulePeriodRepository;
import com.staffsense.demo.service.hr.HrDeskService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт REST-модуля HR: пульт и табель.
 *
 * <p>Проверяем обещания, которые важны клиенту:
 * <ul>
 *   <li>сводку читают HR/ADMIN/EXEC, табель ведут HR/ADMIN, а PM и EMPLOYEE
 *       получают 403 (права не «размазаны» по клиенту);</li>
 *   <li>структура ответа стабильна: периоды, агрегаты, массивы есть всегда —
 *       пустой массив означает «нет данных», а не отсутствие поля;</li>
 *   <li>наружу не утекают JPA-сущности: ни хеша пароля, ни оклада.</li>
 * </ul>
 *
 * <p>Данные для записи создаются свои ({@code hr.employee@test.local}), чтобы
 * не зависеть от демо-дампа и не мешать другим тестам.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HrApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkSchedulePeriodRepository periodRepository;

    private User employee;
    private final List<Long> createdPeriods = new ArrayList<>();

    @BeforeEach
    void setUp() {
        employee = userRepository.findByEmail("hr.employee@test.local").orElseGet(
                () -> userRepository.save(User.builder()
                        .fullName("Сотрудник Табеля")
                        .email("hr.employee@test.local")
                        .passwordHash("hash")
                        .role(Role.EMPLOYEE)
                        .department("Engineering")
                        .position("QA Engineer")
                        .salary(new BigDecimal("70000"))
                        .gradeLevel(GradeLevel.MIDDLE)
                        .weeklyCapacityHours(40)
                        .hireDate(LocalDate.now().minusMonths(2))
                        .active(true)
                        .build()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        for (Long id : createdPeriods) {
            periodRepository.findById(id).ifPresent(periodRepository::delete);
        }
        createdPeriods.clear();
    }

    // ── Доступ ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Сводку читает HR")
    void deskAvailableForHr() throws Exception {
        authenticate("hr@staffsense.com", "ROLE_HR");

        mockMvc.perform(get("/api/v1/hr/desk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.date").exists())
                .andExpect(jsonPath("$.week.from").exists())
                .andExpect(jsonPath("$.week.to").exists())
                .andExpect(jsonPath("$.headcount.total", greaterThan(0)))
                .andExpect(jsonPath("$.headcount.byDepartment").isArray())
                .andExpect(jsonPath("$.probation.months").value(HrDeskService.PROBATION_MONTHS))
                .andExpect(jsonPath("$.probation.basis").value("hireDate"))
                .andExpect(jsonPath("$.probation.items").isArray())
                .andExpect(jsonPath("$.forecast.total", greaterThan(0)))
                .andExpect(jsonPath("$.forecast.items").isArray())
                .andExpect(jsonPath("$.absences.today").isArray())
                .andExpect(jsonPath("$.absences.week").isArray())
                .andExpect(jsonPath("$.absences.upcoming").isArray())
                .andExpect(jsonPath("$.absences.coverage").isArray())
                .andExpect(jsonPath("$.documents.onApproval").exists())
                .andExpect(jsonPath("$.documents.items").isArray());
    }

    @Test
    @DisplayName("Сводку читает EXEC: картина компании без операций")
    void deskAvailableForExec() throws Exception {
        authenticate("exec@staffsense.com", "ROLE_EXEC");

        mockMvc.perform(get("/api/v1/hr/desk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headcount.total", greaterThan(0)));
    }

    @Test
    @DisplayName("Сводка закрыта для PM")
    void deskForbiddenForPm() throws Exception {
        authenticate("pm@staffsense.com", "ROLE_PM");

        mockMvc.perform(get("/api/v1/hr/desk")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Сводка закрыта для сотрудника")
    void deskForbiddenForEmployee() throws Exception {
        authenticate("employee@staffsense.com", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/hr/desk")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Табель закрыт для сотрудника")
    void timesheetForbiddenForEmployee() throws Exception {
        authenticate("employee@staffsense.com", "ROLE_EMPLOYEE");

        mockMvc.perform(get(timesheetUrl())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Табель закрыт для PM, даже по своей команде")
    void timesheetForbiddenForPm() throws Exception {
        authenticate("pm@staffsense.com", "ROLE_PM");

        mockMvc.perform(get(timesheetUrl())).andExpect(status().isForbidden());
    }

    // ── Структура табеля ─────────────────────────────────────────────────

    @Test
    @DisplayName("Табель отдаёт агрегаты по сотруднику и дни периода")
    void timesheetAggregates() throws Exception {
        authenticate("hr@staffsense.com", "ROLE_HR");
        LocalDate from = LocalDate.now().withDayOfMonth(1);
        LocalDate to = LocalDate.now();

        mockMvc.perform(get("/api/v1/hr/timesheet?from=" + from + "&to=" + to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(from.toString()))
                .andExpect(jsonPath("$.to").value(to.toString()))
                .andExpect(jsonPath("$.periodLabel").exists())
                .andExpect(jsonPath("$.department").doesNotExist())
                .andExpect(jsonPath("$.departments", hasItem("Engineering")))
                .andExpect(jsonPath("$.closed").exists())
                .andExpect(jsonPath("$.rows[*].fullName", hasItem("Иван Сидоров")))
                .andExpect(jsonPath("$.rows[0].userId").exists())
                .andExpect(jsonPath("$.rows[0].days").isArray())
                .andExpect(jsonPath("$.rows[0].hasData").exists())
                .andExpect(jsonPath("$.totals.employees", greaterThan(0)))
                .andExpect(jsonPath("$.totals.employeesWithData").exists())
                .andExpect(jsonPath("$.absences").isArray())
                .andExpect(jsonPath("$.absenceRequests").isArray());
    }

    @Test
    @DisplayName("Фильтр по подразделению оставляет только его сотрудников")
    void timesheetFiltersByDepartment() throws Exception {
        authenticate("hr@staffsense.com", "ROLE_HR");

        mockMvc.perform(get("/api/v1/hr/timesheet?department=Engineering"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("Engineering"))
                .andExpect(jsonPath("$.rows[*].department", everyItem(is("Engineering"))));
    }

    @Test
    @DisplayName("Перевёрнутый период отклоняется с 400")
    void rejectsInvertedPeriod() throws Exception {
        authenticate("hr@staffsense.com", "ROLE_HR");

        mockMvc.perform(get("/api/v1/hr/timesheet?from=2026-10-20&to=2026-10-01"))
                .andExpect(status().isBadRequest());
    }

    // ── Отметка часов ────────────────────────────────────────────────────

    @Test
    @DisplayName("HR ставит часы, и они появляются в табеле")
    void hrRecordsHours() throws Exception {
        authenticate("hr@staffsense.com", "ROLE_HR");
        LocalDate day = LocalDate.now();

        mockMvc.perform(post("/api/v1/hr/timesheet")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + employee.getId() + ",\"date\":\"" + day
                                + "\",\"hours\":8.0,\"remote\":true,\"sickLeave\":false,"
                                + "\"vacation\":false,\"overtimeHours\":2.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(employee.getId()))
                .andExpect(jsonPath("$.date").value(day.toString()))
                .andExpect(jsonPath("$.hours").value(8.0))
                .andExpect(jsonPath("$.remote").value(true))
                .andExpect(jsonPath("$.overtimeHours").value(2.0));

        mockMvc.perform(get("/api/v1/hr/timesheet?from=" + day + "&to=" + day
                        + "&department=Engineering"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[?(@.userId==" + employee.getId() + ")].hasData")
                        .value(hasItem(true)))
                .andExpect(jsonPath("$.rows[?(@.userId==" + employee.getId() + ")].remoteDays")
                        .value(hasItem(1)));
    }

    @Test
    @DisplayName("Часы больше суток отклоняются с 400")
    void rejectsImpossibleHours() throws Exception {
        authenticate("hr@staffsense.com", "ROLE_HR");

        mockMvc.perform(post("/api/v1/hr/timesheet")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + employee.getId() + ",\"date\":\""
                                + LocalDate.now() + "\",\"hours\":30}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Закрытый период не редактируется: 409, а не 500")
    void closedPeriodReturnsConflict() throws Exception {
        authenticate("hr@staffsense.com", "ROLE_HR");
        LocalDate day = LocalDate.now();

        WorkSchedulePeriod period = periodRepository.save(WorkSchedulePeriod.builder()
                .manager(employee)
                .periodStart(day.withDayOfMonth(1))
                .periodEnd(day.withDayOfMonth(day.lengthOfMonth()))
                .status(TimesheetPeriodStatus.CLOSED)
                .closedAt(LocalDateTime.now())
                .build());
        createdPeriods.add(period.getId());

        mockMvc.perform(post("/api/v1/hr/timesheet")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + employee.getId() + ",\"date\":\"" + day
                                + "\",\"hours\":8}"))
                .andExpect(status().isConflict());
    }

    // ── Утечки сущностей ─────────────────────────────────────────────────

    @Test
    @DisplayName("В JSON нет ни хеша пароля, ни оклада")
    void noEntityLeaks() throws Exception {
        authenticate("hr@staffsense.com", "ROLE_HR");
        LocalDate from = LocalDate.now().withDayOfMonth(1);

        String desk = mockMvc.perform(get("/api/v1/hr/desk"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String timesheet = mockMvc.perform(get("/api/v1/hr/timesheet?from=" + from
                        + "&to=" + LocalDate.now()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertFalse(desk.contains("passwordHash"), "сводка не должна отдавать хеш пароля");
        assertFalse(desk.contains("salary"), "сводка не должна отдавать оклад");
        assertFalse(timesheet.contains("passwordHash"), "табель не должен отдавать хеш пароля");
        assertFalse(timesheet.contains("salary"), "табель не должен отдавать оклад");
    }

    // ── Вспомогательное ──────────────────────────────────────────────────

    private void authenticate(String email, String authority) {
        SecurityContextHolder.clearContext();
        var token = new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    /** Период по умолчанию: с первого числа текущего месяца по сегодня. */
    private static String timesheetUrl() {
        return "/api/v1/hr/timesheet?from=" + LocalDate.now().withDayOfMonth(1)
                + "&to=" + LocalDate.now();
    }
}
