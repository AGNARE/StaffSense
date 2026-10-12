package com.staffsense.demo.service.metrics;

import com.staffsense.demo.model.KpiRecord;
import com.staffsense.demo.model.SurveyAnswer;
import com.staffsense.demo.model.SurveyResponse;
import com.staffsense.demo.model.User;
import com.staffsense.demo.model.WorkSchedule;
import com.staffsense.demo.model.WorkloadRecord;
import com.staffsense.demo.repository.SurveyAnswerRepository;
import com.staffsense.demo.repository.SurveyResponseRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.repository.kpi.KpiRecordRepository;
import com.staffsense.demo.repository.workload.WorkloadRecordRepository;
import com.staffsense.demo.service.TimesheetService;
import com.staffsense.demo.service.metrics.BurnoutRadarService.BurnoutRadarSnapshot;
import com.staffsense.demo.service.metrics.BurnoutRadarService.EmployeeBurnoutFactors;
import com.staffsense.demo.service.metrics.BurnoutRadarService.FactorValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты «Радара выгорания» (FR-019, WBS 3.4.4).
 *
 * <p>Проверяют:
 * <ul>
 *   <li>расчёт факторов на существующих демо-данных (нагрузка, KPI, благополучие);</li>
 *   <li>ограничение всех значений шкалой 0..100 и итогового балла — средним
 *       только по доступным факторам;</li>
 *   <li>честную обработку отсутствия данных: недоступный фактор — {@code null},
 *       а не подставленный ноль (табель, встречи, опросы);</li>
 *   <li>data-scope: PM видит только свою команду, EMPLOYEE — только себя,
 *       ADMIN/HR/EXEC — всех;</li>
 *   <li>чистые формулы факторов, включая граничные значения.</li>
 * </ul>
 *
 * <p>Тест помечен {@code @Transactional}: изменения табеля откатываются после
 * каждого теста и не влияют на остальные.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BurnoutRadarServiceTest {

    @Autowired
    private BurnoutRadarService burnoutRadarService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkloadRecordRepository workloadRecordRepository;

    @Autowired
    private KpiRecordRepository kpiRecordRepository;

    @Autowired
    private TimesheetService timesheetService;

    @Autowired
    private SurveyResponseRepository surveyResponseRepository;

    @Autowired
    private SurveyAnswerRepository surveyAnswerRepository;

    private User user(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    // ── Расчёт на существующих демо-данных ───────────────────────────────

    @Test
    @DisplayName("FR-019: факторы считаются из демо-данных и согласуются с WorkloadRecord/KpiRecord")
    void factorsAreDerivedFromExistingDemoData() {
        User employee = user("ivan@staffsense.com");

        EmployeeBurnoutFactors profile = burnoutRadarService.profileFor(user("admin@staffsense.com"), employee);

        assertNotNull(profile, "Профиль сотрудника должен быть доступен ADMIN");
        assertEquals(employee.getId(), profile.employeeId());
        assertEquals(employee.getFullName(), profile.employeeName());

        // Нагрузка: демо-данные содержат 6 месяцев WorkloadRecord — фактор обязан быть рассчитан.
        Double workload = profile.value("workload");
        assertNotNull(workload, "Индекс нагрузки есть в демо-данных");
        double expectedWorkload = BurnoutRadarService.workloadFactor(
                workloadRecordRepository.findByUserIdOrderByPeriodEndDesc(employee.getId()).get(0));
        assertEquals(expectedWorkload, workload, 1e-9, "Фактор нагрузки должен совпадать с формулой от WorkloadRecord");

        // KPI: демо-данные содержат 6 месяцев KpiRecord.
        Double kpi = profile.value("kpi");
        assertNotNull(kpi, "KPI есть в демо-данных");
        KpiRecord latestKpi = kpiRecordRepository.findByUserIdOrderByPeriodEndDesc(employee.getId()).get(0);
        assertEquals(Math.round((100.0 - latestKpi.getKpiScore()) * 10.0) / 10.0, kpi, 1e-9,
                "Фактор KPI = инверсия последней оценки KPI");

        // Благополучие: ivan@staffsense.com отвечал на активный пульс-опрос.
        assertNotNull(profile.value("wellbeing"), "По ответам сотрудника фактор благополучия доступен");

        // Переработки: табель не заполнен, поэтому источник — WorkloadRecord.overtimeHours.
        assertNotNull(profile.value("overtime"), "Переработки берутся из WorkloadRecord при пустом табеле");

        // Отсутствия: записей табеля нет — показатель недоступен (не ноль!).
        assertNull(profile.value("absence"), "Без записей табеля фактор отсутствий недоступен, а не равен нулю");
    }

    @Test
    @DisplayName("FR-019: все значения факторов и итоговый балл ограничены шкалой 0..100")
    void allValuesAreWithinZeroToHundred() {
        BurnoutRadarSnapshot snapshot = burnoutRadarService.snapshotFor(user("admin@staffsense.com"));

        assertTrue(snapshot.hasData(), "На демо-данных радар должен содержать данные");
        assertFalse(snapshot.employees().isEmpty(), "ADMIN видит сотрудников");

        for (EmployeeBurnoutFactors profile : snapshot.employees()) {
            for (FactorValue factor : profile.factors()) {
                if (factor.value() == null) {
                    continue;
                }
                assertTrue(factor.value() >= 0.0 && factor.value() <= 100.0,
                        "Фактор " + factor.key() + " вне 0..100: " + factor.value());
            }
            double score = profile.score();
            assertTrue(score >= 0.0 && score <= 100.0, "Балл сотрудника вне 0..100: " + score);
            assertTrue(List.of("LOW", "MEDIUM", "HIGH", "NO_DATA").contains(profile.riskLevel()),
                    "Недопустимый уровень риска: " + profile.riskLevel());
            // Ноль доступных факторов (руководитель без записей нагрузки/KPI) —
            // это «нет данных», а не «низкий риск»: иначе дашборд вводил бы в заблуждение.
            if (profile.availableFactorCount() == 0) {
                assertEquals("NO_DATA", profile.riskLevel(),
                        "Без доступных факторов уровень должен быть NO_DATA, а не LOW");
                assertEquals(0, profile.availableFactorCount());
            }
        }

        Double teamScore = snapshot.teamScore();
        assertNotNull(teamScore);
        assertTrue(teamScore >= 0.0 && teamScore <= 100.0);
    }

    @Test
    @DisplayName("FR-019: итоговый балл — среднее только по доступным факторам")
    void scoreIsAverageOfAvailableFactorsOnly() {
        BurnoutRadarSnapshot snapshot = burnoutRadarService.snapshotFor(user("hr@staffsense.com"));

        for (EmployeeBurnoutFactors profile : snapshot.employees()) {
            List<Double> available = profile.factors().stream()
                    .map(FactorValue::value)
                    .filter(v -> v != null)
                    .toList();
            double expected = available.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            assertEquals(expected, profile.score(), 1e-9,
                    "Балл должен быть средним по доступным факторам (профиль " + profile.employeeName() + ")");
        }
    }

    @Test
    @DisplayName("FR-019: профили отсортированы по убыванию балла выгорания")
    void profilesAreSortedByScoreDescending() {
        BurnoutRadarSnapshot snapshot = burnoutRadarService.snapshotFor(user("exec@staffsense.com"));

        List<Double> scores = snapshot.employees().stream()
                .map(EmployeeBurnoutFactors::score)
                .toList();
        for (int i = 1; i < scores.size(); i++) {
            assertTrue(scores.get(i - 1) >= scores.get(i),
                    "Нарушен порядок сортировки: " + scores.get(i - 1) + " < " + scores.get(i));
        }
    }

    // ── Data-scope ───────────────────────────────────────────────────────

    @Test
    @DisplayName("FR-019 / RBAC: PM видит только свою команду, а не всех сотрудников")
    void pmSeesOwnTeamOnly() {
        User pm = user("pm@staffsense.com");
        User ownTeamMember = user("ivan@staffsense.com");
        User otherTeamMember = user("pavel@staffsense.com");

        assertNotNull(ownTeamMember.getManager(), "Демо-данные: у сотрудника есть руководитель");
        assertEquals(pm.getId(), ownTeamMember.getManager().getId(),
                "Демо-данные: ivan@staffsense.com подчинён PM pm@staffsense.com");
        assertFalse(pm.getId().equals(otherTeamMember.getManager().getId()),
                "Демо-данные: pavel@staffsense.com в другой команде");

        BurnoutRadarSnapshot snapshot = burnoutRadarService.snapshotFor(pm);
        List<Long> visibleIds = snapshot.employees().stream()
                .map(EmployeeBurnoutFactors::employeeId)
                .toList();

        assertTrue(visibleIds.contains(ownTeamMember.getId()), "PM должен видеть свою команду");
        assertTrue(visibleIds.contains(pm.getId()), "PM видит и собственный профиль");
        assertFalse(visibleIds.contains(otherTeamMember.getId()), "PM не должен видеть чужую команду");
        assertTrue(snapshot.employeeCount() < userRepository.findByActive(true).size(),
                "Область видимости PM уже, чем вся компания");

        assertNotNull(burnoutRadarService.profileFor(pm, ownTeamMember));
        assertNull(burnoutRadarService.profileFor(pm, otherTeamMember),
                "Профиль сотрудника чужой команды для PM недоступен");
    }

    @Test
    @DisplayName("FR-019 / RBAC: EMPLOYEE видит только себя")
    void employeeSeesOnlySelf() {
        User employee = user("ivan@staffsense.com");

        BurnoutRadarSnapshot snapshot = burnoutRadarService.snapshotFor(employee);

        assertEquals(1, snapshot.employeeCount(), "EMPLOYEE видит ровно один профиль — свой");
        assertEquals(employee.getId(), snapshot.employees().get(0).employeeId());

        assertNull(burnoutRadarService.profileFor(employee, user("anna@staffsense.com")),
                "EMPLOYEE не должен видеть коллегу по команде");
        assertNotNull(burnoutRadarService.profileFor(employee, employee),
                "Свой профиль EMPLOYEE видеть должен");
    }

    @Test
    @DisplayName("FR-019 / RBAC: ADMIN, HR и EXEC видят всех активных сотрудников")
    void privilegedRolesSeeEveryone() {
        long activeCount = userRepository.findByActive(true).size();

        for (String email : List.of("admin@staffsense.com", "hr@staffsense.com", "exec@staffsense.com")) {
            BurnoutRadarSnapshot snapshot = burnoutRadarService.snapshotFor(user(email));
            assertEquals(activeCount, snapshot.employeeCount(),
                    "Роль " + email + " должна видеть всех активных сотрудников");
        }
    }

    // ── Отсутствие данных ────────────────────────────────────────────────

    @Test
    @DisplayName("FR-019: без персональных ответов на опросы фактор благополучия недоступен")
    void missingDataIsReportedAsUnavailable() {
        User silent = employeeWithoutSurveyAnswers();

        EmployeeBurnoutFactors profile = burnoutRadarService.profileFor(user("admin@staffsense.com"), silent);

        assertNotNull(profile);
        assertNull(profile.value("wellbeing"),
                "Сотрудник не отвечал на опросы — фактор благополучия недоступен, а не «идеальные 0»");
        assertNull(profile.value("absence"),
                "Записей табеля нет — фактор отсутствий недоступен");
        assertTrue(profile.availableFactorCount() < 6,
                "Не все факторы доступны — интерфейс должен показать прочерк");
    }

    @Test
    @DisplayName("FR-019: данные табеля включают переработки и дни отсутствия в расчёт")
    void timesheetDataFeedsOvertimeAndAbsenceFactors() {
        // Табель ведёт HR (ADMIN/HR — любые сотрудники, BR-027); PM ограничен своей командой.
        User hr = user("hr@staffsense.com");
        User employee = employeeWithoutSurveyAnswers();
        LocalDate today = LocalDate.now();

        // До заполнения табеля фактор отсутствий недоступен: показателя нет, а не «ноль дней».
        EmployeeBurnoutFactors before = burnoutRadarService.profileFor(user("admin@staffsense.com"), employee);
        assertNotNull(before);
        assertNull(before.value("absence"), "До заполнения табеля отсутствий нет в данных");

        // 5 дней переработок по 4 часа = 20 ч → фактор переработок 100.
        for (int i = 0; i < 5; i++) {
            timesheetService.saveEntry(hr, employee.getId(), today.minusDays(i),
                    9.0, false, false, false, 4.0);
        }
        // 1 день больничного и 1 день отпуска → фактор отсутствий = 2/20 * 100.
        timesheetService.saveEntry(hr, employee.getId(), today.minusDays(10), 0.0, false, true, false, 0.0);
        timesheetService.saveEntry(hr, employee.getId(), today.minusDays(11), 0.0, false, false, true, 0.0);

        EmployeeBurnoutFactors after = burnoutRadarService.profileFor(user("admin@staffsense.com"), employee);
        assertNotNull(after);
        assertEquals(100.0, after.value("overtime"), 1e-9,
                "20 часов переработок за окно — максимум шкалы фактора");
        assertEquals(10.0, after.value("absence"), 1e-9,
                "2 дня отсутствия из 20 возможных — 10 баллов фактора");
        assertTrue(after.score() >= before.score(),
                "Добавление переработок и отсутствий не должно снижать балл выгорания");
    }

    @Test
    @DisplayName("FR-019: фактор благополучия персонален — только по своим ответам (LIKERT-5)")
    void surveyWellbeingIsPerEmployeeOnly() {
        User answered = employeeWithSurveyAnswers();
        User silent = employeeWithoutSurveyAnswers();

        EmployeeBurnoutFactors withAnswers = burnoutRadarService.profileFor(user("admin@staffsense.com"), answered);
        EmployeeBurnoutFactors withoutAnswers = burnoutRadarService.profileFor(user("admin@staffsense.com"), silent);

        assertNotNull(withAnswers);
        assertNotNull(withoutAnswers);
        // Анонимные ответы (user_id = null) не персонифицируются: фактор считается
        // ровно по ответам с user_id этого сотрудника и совпадает с формулой.
        assertEquals(expectedWellbeingFactor(answered), withAnswers.value("wellbeing"), 1e-9,
                "Фактор благополучия должен совпадать с инверсией средней оценки LIKERT-5");
        assertNull(withoutAnswers.value("wellbeing"),
                "Нет персональных ответов — нет персонального фактора благополучия");
    }

    @Test
    @DisplayName("FR-019: пустая область видимости не даёт исключения и не выдумывает данные")
    void emptyScopeProducesHonestEmptyState() {
        BurnoutRadarSnapshot snapshot = burnoutRadarService.snapshotFor(null);

        assertTrue(snapshot.employees().isEmpty(), "Без актора область видимости пуста");
        assertEquals(0, snapshot.employeeCount());
        assertNull(snapshot.teamScore(), "Средний балл без сотрудников отсутствует");
        assertFalse(snapshot.hasData());
    }

    // ── Чистые формулы (без БД) ──────────────────────────────────────────

    @Test
    @DisplayName("Фактор нагрузки: 0 при индексе ≤ 1.3, 100 при индексе ≥ 2.0")
    void workloadFactorBoundaries() {
        assertEquals(0.0, BurnoutRadarService.workloadFactor(workload(1.0)), 1e-9);
        assertEquals(0.0, BurnoutRadarService.workloadFactor(workload(1.3)), 1e-9,
                "Порог критической перегрузки BR-002 — начало вклада фактора");
        assertEquals(100.0, BurnoutRadarService.workloadFactor(workload(2.0)), 1e-9);
        assertEquals(100.0, BurnoutRadarService.workloadFactor(workload(3.5)), 1e-9,
                "Выше потолка значение ограничивается 100");
        assertEquals(50.0, BurnoutRadarService.workloadFactor(workload(1.65)), 1e-9,
                "Середина диапазона 1.3..2.0 даёт половину шкалы");
        assertNull(BurnoutRadarService.workloadFactor(null),
                "Без записи нагрузки показатель недоступен");
    }

    @Test
    @DisplayName("Фактор KPI: инверсия оценки, ограниченная шкалой 0..100")
    void kpiFactorBoundaries() {
        assertEquals(0.0, BurnoutRadarService.kpiFactor(kpi(100.0)), 1e-9, "KPI 100 — вклада нет");
        assertEquals(100.0, BurnoutRadarService.kpiFactor(kpi(0.0)), 1e-9, "KPI 0 — максимум вклада");
        assertEquals(30.0, BurnoutRadarService.kpiFactor(kpi(70.0)), 1e-9);
        assertEquals(0.0, BurnoutRadarService.kpiFactor(kpi(150.0)), 1e-9,
                "Оценка выше шкалы ограничивается");
        assertNull(BurnoutRadarService.kpiFactor(null), "Без записи KPI показатель недоступен");
    }

    @Test
    @DisplayName("Фактор благополучия недоступен без ответов: отсутствие данных — не ноль")
    void absenceFactorWithoutTimesheetIsUnavailable() {
        assertNull(BurnoutRadarService.absenceFactor(List.of()),
                "Пустой табель — показатель недоступен");
        assertNull(BurnoutRadarService.missedOneOnOneFactor(List.of()),
                "Нет встреч — показатель недоступен");
    }

    @Test
    @DisplayName("Фактор переработок: табель приоритетнее, иначе WorkloadRecord, иначе нет данных")
    void overtimeFactorFallbackChain() {
        // Табель заполнен — берутся часы из табеля (10 ч из 20 → половина шкалы),
        // даже если в записи нагрузки переработок больше.
        assertEquals(50.0, BurnoutRadarService.overtimeFactor(
                List.of(schedule(10.0), schedule(0.0)),
                WorkloadRecord.builder().overtimeHours(20.0).build()), 1e-9);

        // Табель пуст — откат на сохранённые часы переработок WorkloadRecord.
        assertEquals(50.0, BurnoutRadarService.overtimeFactor(List.of(),
                WorkloadRecord.builder().overtimeHours(10.0).build()), 1e-9);

        // Ни табеля, ни записи нагрузки — показатель недоступен.
        assertNull(BurnoutRadarService.overtimeFactor(List.of(), null));

        // Переработки выше нормы ограничиваются максимумом шкалы.
        assertEquals(100.0, BurnoutRadarService.overtimeFactor(List.of(),
                WorkloadRecord.builder().overtimeHours(35.0).build()), 1e-9);
    }

    @Test
    @DisplayName("Фактор отсутствий: дни больничного и отпуска (BR-030) из табеля")
    void absenceFactorCountsSickLeaveAndVacation() {
        assertEquals(0.0, BurnoutRadarService.absenceFactor(List.of(schedule(0.0))), 1e-9,
                "Табель заполнен, но отсутствий нет — это честный ноль, а не отсутствие данных");
        assertEquals(25.0, BurnoutRadarService.absenceFactor(
                List.of(sickLeave(), sickLeave(), vacationDay(), vacationDay(),
                        sickLeave(), schedule(8.0))), 1e-9,
                "5 дней отсутствия из 20 возможных — четверть шкалы");
    }

    // ── Вспомогательные ──────────────────────────────────────────────────

    /**
     * Сотрудник, у которого нет ни одного персонального ответа на опросы.
     *
     * <p>Набор респондентов в демо-данных может меняться (сид-генератор и
     * правки в {@code DataInitializer}), поэтому тест не привязывается к
     * конкретному e-mail: если такого сотрудника нет вовсе, тест помечается
     * пропущенным (assumption), а не падает.
     */
    private User employeeWithoutSurveyAnswers() {
        return userRepository.findByActive(true).stream()
                .filter(u -> surveyResponseRepository.findByUserId(u.getId()).isEmpty())
                .findFirst()
                .orElseGet(() -> {
                    org.junit.jupiter.api.Assumptions.assumeTrue(false,
                            "В демо-данных все сотрудники ответили на опросы — проверка отсутствия данных неприменима");
                    return null;
                });
    }

    /** Сотрудник, у которого есть персональные ответы с оценками LIKERT-5. */
    private User employeeWithSurveyAnswers() {
        return userRepository.findByActive(true).stream()
                .filter(u -> expectedWellbeingFactor(u) != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("В демо-данных нет ни одного ответа с оценкой LIKERT-5"));
    }

    /** Ожидаемый фактор благополучия — инверсия средней оценки LIKERT-5. */
    private Double expectedWellbeingFactor(User user) {
        List<SurveyResponse> responses = surveyResponseRepository.findByUserId(user.getId());
        if (responses.isEmpty()) {
            return null;
        }
        List<Integer> values = surveyAnswerRepository
                .findByResponseIdIn(responses.stream().map(SurveyResponse::getId).toList())
                .stream()
                .map(SurveyAnswer::getLikertValue)
                .filter(v -> v != null)
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        double average = values.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        double norm = Math.min(1.0, Math.max(0.0, (average - 1.0) / 4.0));
        return Math.round(100.0 * (1.0 - norm) * 10.0) / 10.0;
    }

    private WorkloadRecord workload(double index) {
        return WorkloadRecord.builder()
                .workloadIndex(index)
                .overtimeHours(0.0)
                .build();
    }

    private KpiRecord kpi(double score) {
        KpiRecord record = new KpiRecord();
        record.setKpiScore(score);
        return record;
    }

    private WorkSchedule schedule(double overtimeHours) {
        WorkSchedule entry = new WorkSchedule();
        entry.setOvertimeHours(overtimeHours);
        return entry;
    }

    private WorkSchedule sickLeave() {
        WorkSchedule entry = new WorkSchedule();
        entry.setSickLeave(true);
        return entry;
    }

    private WorkSchedule vacationDay() {
        WorkSchedule entry = new WorkSchedule();
        entry.setVacation(true);
        return entry;
    }
}
