package com.staffsense.demo.journey;

import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.TimesheetPeriodStatus;
import com.staffsense.demo.model.User;
import com.staffsense.demo.model.WorkSchedule;
import com.staffsense.demo.model.WorkSchedulePeriod;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.repository.WorkSchedulePeriodRepository;
import com.staffsense.demo.repository.WorkScheduleRepository;
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
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозной сценарий HR: пульт и табель.
 *
 * <p>Проверяем путь «действие → последствие», а не отдельные ручки:
 * <ol>
 *   <li>HR строит табель за период, ставит часы сотруднику — и они появляются
 *       и в строке сотрудника, и в сумме отчёта (одно действие, два следствия);</li>
 *   <li>PM не может ни прочитать, ни вести табель: часы HR-уровня — 403;</li>
 *   <li>часы в закрытый период (BR-028) отклоняются конфликтом 409, и запись
 *       не появляется в базе;</li>
 *   <li>отпуск, отмеченный HR в табеле, меняет картину «Пульта HR»: сотрудник
 *       попадает в отсутствия, а его подразделение — в покрытие.</li>
 * </ol>
 *
 * <p>Тест транзакционный: пользователи, часы и закрытые периоды откатываются
 * вместе с тестовой транзакцией. Отдел у тестовых сотрудников свой
 * ({@value #DEPARTMENT}), чтобы суммы отчёта не зависели от демо-дампа и
 * тестов других модулей, которые пишут табель без отката.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HrJourneyTest {

    private static final String HR_EMAIL = "journey.hr.desk@test.local";
    private static final String PM_EMAIL = "journey.hr.desk.pm@test.local";
    private static final String EMPLOYEE_EMAIL = "journey.hr.desk.employee@test.local";

    /** Отдел только для этого класса: фильтр табеля даёт ровно одного человека. */
    private static final String DEPARTMENT = "Отдел табельных путешествий";

    /** Месяц, за который строится табель в основном сценарии. */
    private static final LocalDate PERIOD_FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate PERIOD_TO = LocalDate.of(2026, 9, 30);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkScheduleRepository scheduleRepository;

    @Autowired
    private WorkSchedulePeriodRepository periodRepository;

    private User hr;
    private User pm;
    private User employee;

    @BeforeEach
    void setUp() {
        hr = ensureUser(HR_EMAIL, "Кадровик Табельного Пути", Role.HR, "Кадровая служба", null);
        pm = ensureUser(PM_EMAIL, "Руководитель Табельного Пути", Role.PM, "Руководство", null);
        employee = ensureUser(EMPLOYEE_EMAIL, "Сотрудник Табельного Пути", Role.EMPLOYEE, DEPARTMENT, pm);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    // ── Сценарий: часы появляются в отчёте и в сумме ─────────────────────

    @Test
    @DisplayName("HR ставит часы — они появляются в строке сотрудника и в сумме табеля")
    void hoursAppearInReportAndTotals() throws Exception {
        authenticate(HR_EMAIL, "ROLE_HR");

        // 1. Табель за период строится ещё без данных: строка есть, часов нет.
        mockMvc.perform(get(timesheetUrl(PERIOD_FROM, PERIOD_TO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(PERIOD_FROM.toString()))
                .andExpect(jsonPath("$.to").value(PERIOD_TO.toString()))
                .andExpect(jsonPath("$.department").value(DEPARTMENT))
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].userId").value(employee.getId()))
                .andExpect(jsonPath("$.rows[0].hasData").value(false))
                .andExpect(jsonPath("$.rows[0].totalHours").value(0.0))
                .andExpect(jsonPath("$.totals.employees").value(1))
                .andExpect(jsonPath("$.totals.employeesWithData").value(0))
                .andExpect(jsonPath("$.totals.totalHours").value(0.0));

        // 2. HR ставит два дня: удалённый день с переработкой и обычный день.
        LocalDate remoteDay = LocalDate.of(2026, 9, 7);
        LocalDate officeDay = LocalDate.of(2026, 9, 8);

        mockMvc.perform(post("/api/v1/hr/timesheet")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"date":"%s","hours":6.5,"remote":true,
                                 "sickLeave":false,"vacation":false,"overtimeHours":1.5}
                                """.formatted(employee.getId(), remoteDay)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(employee.getId()))
                .andExpect(jsonPath("$.date").value(remoteDay.toString()))
                .andExpect(jsonPath("$.hours").value(6.5))
                .andExpect(jsonPath("$.remote").value(true))
                .andExpect(jsonPath("$.overtimeHours").value(1.5));

        mockMvc.perform(post("/api/v1/hr/timesheet")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"date":"%s","hours":4.0,"remote":false,
                                 "sickLeave":false,"vacation":false}
                                """.formatted(employee.getId(), officeDay)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours").value(4.0))
                .andExpect(jsonPath("$.overtimeHours").value(0.0));

        // 3. Последствие видно и в строке сотрудника, и в итогах отчёта.
        mockMvc.perform(get(timesheetUrl(PERIOD_FROM, PERIOD_TO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].hasData").value(true))
                .andExpect(jsonPath("$.rows[0].totalHours").value(10.5))
                .andExpect(jsonPath("$.rows[0].overtimeHours").value(1.5))
                .andExpect(jsonPath("$.rows[0].workingDays").value(2))
                .andExpect(jsonPath("$.rows[0].remoteDays").value(1))
                .andExpect(jsonPath("$.rows[0].days.length()").value(2))
                .andExpect(jsonPath("$.totals.employees").value(1))
                .andExpect(jsonPath("$.totals.employeesWithData").value(1))
                .andExpect(jsonPath("$.totals.totalHours").value(10.5))
                .andExpect(jsonPath("$.totals.overtimeHours").value(1.5))
                .andExpect(jsonPath("$.totals.workingDays").value(2))
                .andExpect(jsonPath("$.totals.remoteDays").value(1));

        // 4. Следствие есть и в базе, а не только в ответе ручки.
        WorkSchedule saved = scheduleRepository
                .findByUserIdAndWorkDate(employee.getId(), remoteDay)
                .orElseThrow(() -> new AssertionError("Запись табеля не сохранена"));
        assertEquals(6.5, saved.getHoursWorked(), 1e-9);
        assertEquals(hr.getId(), saved.getCreatedBy().getId(),
                "в записи табеля должен быть указан автор — HR");
    }

    // ── Сценарий: PM не пускают в табель ─────────────────────────────────

    @Test
    @DisplayName("PM получает 403 и на чтение, и на запись табеля — и часы не появляются")
    void pmCannotReadOrEditTimesheet() throws Exception {
        LocalDate day = LocalDate.of(2026, 9, 7);

        authenticate(PM_EMAIL, "ROLE_PM");

        mockMvc.perform(get(timesheetUrl(PERIOD_FROM, PERIOD_TO)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/hr/timesheet")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"date":"%s","hours":8.0}
                                """.formatted(employee.getId(), day)))
                .andExpect(status().isForbidden());

        // Отказ должен быть настоящим: записи в табеле не остаётся.
        Optional<WorkSchedule> entry = scheduleRepository
                .findByUserIdAndWorkDate(employee.getId(), day);
        assertTrue(entry.isEmpty(), "PM не должен был создать запись табеля");
    }

    // ── Сценарий: закрытый период (BR-028) ───────────────────────────────

    @Test
    @DisplayName("Часы в закрытый период отклоняются с 409, и табель помечается закрытым")
    void closedPeriodRejectsHoursWithConflict() throws Exception {
        authenticate(HR_EMAIL, "ROLE_HR");

        // Период ведётся по руководителю команды сотрудника (TimesheetService#isDateLocked).
        periodRepository.save(WorkSchedulePeriod.builder()
                .manager(pm)
                .periodStart(LocalDate.of(2026, 8, 1))
                .periodEnd(LocalDate.of(2026, 8, 31))
                .status(TimesheetPeriodStatus.CLOSED)
                .closedAt(LocalDateTime.now())
                .closedBy(hr)
                .build());

        LocalDate day = LocalDate.of(2026, 8, 12);

        mockMvc.perform(post("/api/v1/hr/timesheet")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"date":"%s","hours":8.0}
                                """.formatted(employee.getId(), day)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("закрыт")));

        assertTrue(scheduleRepository.findByUserIdAndWorkDate(employee.getId(), day).isEmpty(),
                "часы в закрытый период не должны сохраняться");

        // Отчёт за закрытый месяц честно говорит клиенту, что период закрыт.
        mockMvc.perform(get(timesheetUrl(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(true))
                .andExpect(jsonPath("$.rows[0].hasData").value(false));
    }

    // ── Сценарий: отметка отпуска меняет пульт HR ────────────────────────

    @Test
    @DisplayName("Отпуск, отмеченный HR в табеле, попадает в отсутствия пульта HR")
    void vacationMarkedInTimesheetAppearsOnDesk() throws Exception {
        authenticate(HR_EMAIL, "ROLE_HR");

        LocalDate today = LocalDate.now();

        mockMvc.perform(post("/api/v1/hr/timesheet")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"date":"%s","vacation":true}
                                """.formatted(employee.getId(), today)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vacation").value(true))
                .andExpect(jsonPath("$.hours").doesNotExist());

        mockMvc.perform(get("/api/v1/hr/desk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.headcount.total").isNumber())
                // Сотрудник отсутствует: тип «Отпуск», источник — отметка табеля.
                .andExpect(jsonPath("$.absences.today[?(@.userId==" + employee.getId() + ")].type")
                        .value(hasItem("VACATION")))
                .andExpect(jsonPath("$.absences.today[?(@.userId==" + employee.getId() + ")].source")
                        .value(hasItem("TIMESHEET")))
                // И он же — в покрытии своего подразделения: 1 из 1 отсутствует.
                .andExpect(jsonPath("$.absences.coverage[?(@.department=='" + DEPARTMENT + "')].absent")
                        .value(hasItem(1)))
                .andExpect(jsonPath("$.absences.coverage[?(@.department=='" + DEPARTMENT + "')].headcount")
                        .value(hasItem(1)));
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    /** Табель по отделу этого класса: суммы не смешиваются с чужими данными. */
    private static String timesheetUrl(LocalDate from, LocalDate to) {
        return "/api/v1/hr/timesheet?from=" + from + "&to=" + to + "&department=" + DEPARTMENT;
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

    /**
     * Пользователь для сценария. Внутри тестовой транзакции «предыдущего
     * прогона» быть не может, но приводим поля к ожидаемым явно: так тест
     * не зависит от того, что осталось в базе от других классов.
     */
    private User ensureUser(String email, String fullName, Role role, String department, User manager) {
        User user = userRepository.findByEmail(email).orElseGet(User::new);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setDepartment(department);
        user.setPosition("Specialist");
        user.setSalary(new BigDecimal("90000"));
        user.setGradeLevel(GradeLevel.MIDDLE);
        user.setWeeklyCapacityHours(40);
        user.setHireDate(LocalDate.of(2023, 5, 1));
        user.setActive(true);
        user.setManager(manager);
        return userRepository.save(user);
    }
}
