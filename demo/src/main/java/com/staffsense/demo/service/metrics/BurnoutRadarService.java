package com.staffsense.demo.service.metrics;

import com.staffsense.demo.model.KpiRecord;
import com.staffsense.demo.model.OneOnOneMeeting;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.SurveyAnswer;
import com.staffsense.demo.model.SurveyResponse;
import com.staffsense.demo.model.User;
import com.staffsense.demo.model.WorkSchedule;
import com.staffsense.demo.model.WorkloadRecord;
import com.staffsense.demo.repository.OneOnOneMeetingRepository;
import com.staffsense.demo.repository.SurveyAnswerRepository;
import com.staffsense.demo.repository.SurveyResponseRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.TeamAccessService;
import com.staffsense.demo.service.TimesheetService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * «Радар выгорания» — расчёт факторов риска выгорания сотрудника (FR-019).
 *
 * <p><b>Формулировка требования (ТЗ V1.1, FR-019 «Управленческий дашборд
 * (Burnout Radar)», WBS 3.4.4):</b>
 * <blockquote>
 * {@code /dashboard} для не-EMPLOYEE: число активных сотрудников, количество
 * HIGH/MEDIUM/LOW, средний риск, 5 последних прогнозов, 5 ближайших встреч 1:1
 * и 5 активных опросов. Целевой «Радар выгорания» (WBS 3.4.4) — расширенные
 * дашборды аналитики. Роль: ADMIN, EXEC, HR, PM. Предусловие: роль не EMPLOYEE.
 * </blockquote>
 * Базовый {@code /dashboard} реализован в {@code DashboardController}; этот
 * сервис закрывает целевую часть — агрегаты-факторы выгорания для радар-диаграммы
 * на дашборде. Состав метрик в ТЗ не зафиксирован («расширенные дашборды
 * аналитики»), поэтому факторы собраны из уже существующих данных проекта
 * (те же признаки, что использует ML-модель, см. {@code ModelInsightService}).
 *
 * <p><b>Факторы (все — вклад в выгорание, шкала 0..100, больше = хуже):</b>
 * <ol>
 *   <li><b>Нагрузка</b> — индекс нагрузки из {@code WorkloadRecord}
 *       ({@link WorkloadCalculationService}); 0 при индексе ≤ {@value #WORKLOAD_OVERLOAD_THRESHOLD},
 *       100 при индексе ≥ {@value #WORKLOAD_CEILING}. Совпадает с порогом
 *       критической перегрузки BR-002.</li>
 *   <li><b>KPI</b> — инверсия последней оценки KPI
 *       ({@link KpiCalculationService}, {@code KpiRecord#getKpiScore} перевод
 *       в шкалу 0..1): {@code (1 − kpiNorm) × 100}.</li>
 *   <li><b>Переработки</b> — сумма часов переработок за окно из табеля
 *       ({@link TimesheetService}), с откатом на
 *       {@code WorkloadRecord#getOvertimeHours}; нормализация по
 *       {@value #OVERTIME_CAP_HOURS} ч.</li>
 *   <li><b>Отсутствия</b> — дни больничного и отпуска за окно
 *       ({@link TimesheetService#countAbsenceDays}); нормализация по
 *       {@value #ABSENCE_CAP_DAYS} дням.</li>
 *   <li><b>Встречи 1:1</b> — доля пропущенных встреч за окно
 *       ({@code OneOnOneMeeting}, признак {@code missed} — FR-026/BR-021).</li>
 *   <li><b>Благополучие</b> — инверсия средней оценки LIKERT-5 из ответов на
 *       опросы ({@code SurveyResponse}/{@code SurveyAnswer}); анонимные ответы
 *       (без {@code user_id}) не персонифицируются и в фактор не попадают.</li>
 * </ol>
 *
 * <p>Итоговый балл выгорания — среднее только по <b>доступным</b> факторам.
 * Недоступный показатель (нет записей нагрузки, KPI, табеля, встреч или
 * ответов на опросы) не подменяется нулём и не выдумывается: фактор помечается
 * как отсутствующий и исключается из расчёта.
 *
 * <p><b>Ограничение по данным (data-scope):</b> ADMIN/HR/EXEC видят всех
 * активных сотрудников, PM — только свою команду (подчинённые по
 * {@code users.manager_id}) и себя, EMPLOYEE — только себя. Проверка — через
 * {@link TeamAccessService#canViewEmployee(User, User)}, как в остальных
 * контроллерах и сервисах проекта.
 */
@Service
@RequiredArgsConstructor
public class BurnoutRadarService {

    private static final Logger log = LoggerFactory.getLogger(BurnoutRadarService.class);

    /** Окно анализа факторов «свежих» показателей (дни). */
    public static final int RADAR_WINDOW_DAYS = 90;

    /** Порог индекса нагрузки, с которого начинается вклад в выгорание (BR-002). */
    public static final double WORKLOAD_OVERLOAD_THRESHOLD = 1.3;

    /** Индекс нагрузки, дающий максимальный вклад фактора (100). */
    public static final double WORKLOAD_CEILING = 2.0;

    /** Часы переработок за окно, дающие максимальный вклад фактора (100). */
    public static final double OVERTIME_CAP_HOURS = 20.0;

    /** Дни отсутствия за окно, дающие максимальный вклад фактора (100). */
    public static final double ABSENCE_CAP_DAYS = 20.0;

    /** Порог итогового балла: ниже — низкий риск выгорания. */
    public static final double LOW_RISK_THRESHOLD = 30.0;

    /** Порог итогового балла: выше — высокий риск выгорания. */
    public static final double HIGH_RISK_THRESHOLD = 60.0;

    /** Порядок и подписи факторов радара (ключ, подпись). */
    private static final List<Map.Entry<String, String>> FACTOR_DEFINITIONS = List.of(
            Map.entry("workload", "Нагрузка"),
            Map.entry("kpi", "KPI"),
            Map.entry("overtime", "Переработки"),
            Map.entry("absence", "Отсутствия"),
            Map.entry("missed1on1", "Встречи 1:1"),
            Map.entry("wellbeing", "Благополучие"));

    private final UserRepository userRepository;
    private final TeamAccessService teamAccessService;
    private final WorkloadCalculationService workloadCalculationService;
    private final KpiCalculationService kpiCalculationService;
    private final TimesheetService timesheetService;
    private final OneOnOneMeetingRepository oneOnOneMeetingRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;

    // ── Основной сценарий (FR-019) ───────────────────────────────────────

    /**
     * Снимок «Радара выгорания» для актора: профили видимых сотрудников,
     * усреднённый профиль и итоговый балл.
     *
     * @param actor текущий пользователь (ADMIN, EXEC, HR, PM или EMPLOYEE)
     */
    @Transactional(readOnly = true)
    public BurnoutRadarSnapshot snapshotFor(User actor) {
        List<User> employees = visibleEmployees(actor);
        LocalDate from = LocalDate.now().minusDays(RADAR_WINDOW_DAYS);
        LocalDate to = LocalDate.now();

        List<EmployeeBurnoutFactors> profiles = new ArrayList<>();
        for (User employee : employees) {
            profiles.add(buildProfile(employee, from, to));
        }
        profiles.sort(Comparator
                .comparing(EmployeeBurnoutFactors::score, Comparator.reverseOrder())
                .thenComparing(EmployeeBurnoutFactors::employeeName,
                        Comparator.nullsLast(String::compareTo)));

        List<FactorValue> teamAverages = averageFactors(profiles);

        log.debug("Burnout radar for {} ({}): {} employee(s), available factors {}",
                actor != null ? actor.getEmail() : null, actor != null ? actor.getRole() : null,
                profiles.size(), availableKeys(profiles));

        return new BurnoutRadarSnapshot(profiles, teamAverages, factorLabels(),
                availableKeys(profiles), RADAR_WINDOW_DAYS);
    }

    /**
     * Профиль выгорания одного сотрудника. Возвращает {@code null}, если
     * актору не разрешено видеть этого сотрудника (data-scope).
     */
    @Transactional(readOnly = true)
    public EmployeeBurnoutFactors profileFor(User actor, User employee) {
        if (!teamAccessService.canViewEmployee(actor, employee)) {
            return null;
        }
        return buildProfile(employee, LocalDate.now().minusDays(RADAR_WINDOW_DAYS), LocalDate.now());
    }

    /**
     * Сотрудники, попадающие в область видимости актора (FR-019 + RBAC):
     * ADMIN/HR/EXEC — все активные; PM — своя команда и сам PM;
     * EMPLOYEE — только себя.
     */
    public List<User> visibleEmployees(User actor) {
        if (actor == null) {
            return List.of();
        }
        List<User> active = userRepository.findByActive(true).stream()
                .filter(u -> teamAccessService.canViewEmployee(actor, u))
                .sorted(Comparator.comparing(User::getFullName, Comparator.nullsLast(String::compareTo)))
                .toList();

        if (actor.getRole() == Role.EMPLOYEE) {
            // EMPLOYEE видит только себя — даже если по manager_id попал в чью-то команду.
            return active.stream()
                    .filter(u -> actor.getId() != null && actor.getId().equals(u.getId()))
                    .toList();
        }
        return active;
    }

    // ── Расчёт профиля ───────────────────────────────────────────────────

    private EmployeeBurnoutFactors buildProfile(User employee, LocalDate from, LocalDate to) {
        List<WorkloadRecord> workloads =
                workloadCalculationService.getHistory(employee.getId());
        WorkloadRecord latestWorkload = workloads.isEmpty() ? null : workloads.get(0);

        List<KpiRecord> kpis = kpiCalculationService.getHistory(employee.getId());
        KpiRecord latestKpi = kpis.isEmpty() ? null : kpis.get(0);

        List<WorkSchedule> schedule = timesheetService.getEntries(employee.getId(), from, to);
        List<OneOnOneMeeting> meetings = oneOnOneMeetingRepository.findByEmployeeId(employee.getId())
                .stream()
                .filter(m -> m.getScheduledAt() != null && !m.getScheduledAt().isBefore(from.atStartOfDay()))
                .toList();

        List<FactorValue> factors = FACTOR_DEFINITIONS.stream()
                .map(def -> new FactorValue(def.getKey(), def.getValue(),
                        factorValue(def.getKey(), latestWorkload, latestKpi, schedule, meetings, employee)))
                .toList();

        return new EmployeeBurnoutFactors(employee.getId(), employee.getFullName(),
                employee.getDepartment(), factors);
    }

    /** Значение одного фактора в шкале вклада в выгорание либо {@code null}, если данных нет. */
    private Double factorValue(String key,
                              WorkloadRecord workload,
                              KpiRecord kpi,
                              List<WorkSchedule> schedule,
                              List<OneOnOneMeeting> meetings,
                              User employee) {
        return switch (key) {
            case "workload" -> workloadFactor(workload);
            case "kpi" -> kpiFactor(kpi);
            case "overtime" -> overtimeFactor(schedule, workload);
            case "absence" -> absenceFactor(schedule);
            case "missed1on1" -> missedOneOnOneFactor(meetings);
            case "wellbeing" -> wellbeingFactor(employee);
            default -> null;
        };
    }

    /** Фактор нагрузки: 0 при индексе ≤ 1.3 (BR-002), 100 при индексе ≥ 2.0. */
    static Double workloadFactor(WorkloadRecord workload) {
        if (workload == null || workload.getWorkloadIndex() == null) {
            return null;
        }
        double index = workload.getWorkloadIndex();
        if (index <= WORKLOAD_OVERLOAD_THRESHOLD) {
            return 0.0;
        }
        return scale(index, WORKLOAD_OVERLOAD_THRESHOLD, WORKLOAD_CEILING);
    }

    /** Фактор KPI: низкая результативность → высокий вклад в выгорание. */
    static Double kpiFactor(KpiRecord kpi) {
        if (kpi == null || kpi.getKpiScore() == null) {
            return null;
        }
        double norm = clamp(kpi.getKpiScore() / 100.0);
        return round(100.0 * (1.0 - norm));
    }

    /**
     * Фактор переработок: сначала часы из табеля (если табель заполнен),
     * иначе — сохранённые часы переработок в записи нагрузки
     * ({@code WorkloadCalculationService} пересчитывает и хранит их вместе
     * с индексом нагрузки). Нет ни того, ни другого — фактор недоступен.
     */
    static Double overtimeFactor(List<WorkSchedule> schedule, WorkloadRecord workload) {
        if (schedule != null && !schedule.isEmpty()) {
            double hours = schedule.stream()
                    .mapToDouble(e -> e.getOvertimeHours() != null ? e.getOvertimeHours() : 0.0)
                    .sum();
            return scale(Math.max(0.0, hours), 0.0, OVERTIME_CAP_HOURS);
        }
        if (workload != null && workload.getOvertimeHours() != null) {
            return scale(Math.max(0.0, workload.getOvertimeHours()), 0.0, OVERTIME_CAP_HOURS);
        }
        return null;
    }

    /**
     * Фактор отсутствий (BR-030: утверждённые больничный/отпуск попадают в табель).
     * Нет ни одной записи табеля за окно — показатель недоступен, а не «ноль дней».
     */
    static Double absenceFactor(List<WorkSchedule> schedule) {
        if (schedule == null || schedule.isEmpty()) {
            return null;
        }
        long absenceDays = schedule.stream()
                .filter(e -> e.isSickLeave() || e.isVacation())
                .count();
        return scale(absenceDays, 0.0, ABSENCE_CAP_DAYS);
    }

    /**
     * Фактор пропущенных встреч 1:1: доля встреч с признаком {@code missed}
     * среди встреч за окно. Встреч нет — показатель недоступен.
     */
    static Double missedOneOnOneFactor(List<OneOnOneMeeting> meetings) {
        if (meetings == null || meetings.isEmpty()) {
            return null;
        }
        long missed = meetings.stream().filter(m -> Boolean.TRUE.equals(m.getMissed())).count();
        return round(100.0 * missed / meetings.size());
    }

    /**
     * Фактор благополучия: инверсия средней оценки LIKERT-5 по ответам
     * сотрудника. Анонимные ответы ({@code user_id = null}) не персонифицируются
     * и здесь не учитываются — честное отсутствие данных вместо подстановки.
     */
    private Double wellbeingFactor(User employee) {
        int[] result = likertStats(employee.getId());
        if (result == null) {
            return null;
        }
        double average = (double) result[0] / result[1];
        double norm = clamp((average - 1.0) / 4.0);
        return round(100.0 * (1.0 - norm));
    }

    /** @return {@code [сумма оценок, количество оценок]} либо {@code null}, если оценок нет. */
    private int[] likertStats(Long userId) {
        List<SurveyResponse> responses = surveyResponseRepository.findByUserId(userId);
        if (responses.isEmpty()) {
            return null;
        }
        List<Long> responseIds = responses.stream().map(SurveyResponse::getId).toList();
        List<SurveyAnswer> answers = surveyAnswerRepository.findByResponseIdIn(responseIds).stream()
                .filter(a -> a.getLikertValue() != null)
                .toList();
        if (answers.isEmpty()) {
            return null;
        }
        int sum = answers.stream().mapToInt(SurveyAnswer::getLikertValue).sum();
        return new int[]{sum, answers.size()};
    }

    // ── Агрегация ────────────────────────────────────────────────────────

    /** Усреднение факторов по доступным значениям (недоступные не подменяются нулём). */
    private static List<FactorValue> averageFactors(List<EmployeeBurnoutFactors> profiles) {
        List<FactorValue> result = new ArrayList<>();
        for (Map.Entry<String, String> def : FACTOR_DEFINITIONS) {
            List<Double> values = profiles.stream()
                    .map(p -> p.value(def.getKey()))
                    .filter(v -> v != null)
                    .toList();
            Double average = values.isEmpty()
                    ? null
                    : round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
            result.add(new FactorValue(def.getKey(), def.getValue(), average));
        }
        return result;
    }

    /** Ключи факторов, по которым есть данные хотя бы у одного видимого сотрудника. */
    private static Set<String> availableKeys(List<EmployeeBurnoutFactors> profiles) {
        return FACTOR_DEFINITIONS.stream()
                .map(Map.Entry::getKey)
                .filter(key -> profiles.stream().anyMatch(p -> p.value(key) != null))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static List<Map.Entry<String, String>> factorLabels() {
        return new ArrayList<>(FACTOR_DEFINITIONS);
    }

    /** Общая справка «откуда взяты данные» — выводится на дашборде. */
    public Map<String, String> factorSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("workload", "Индекс нагрузки (WorkloadRecord, WorkloadCalculationService)");
        sources.put("kpi", "Оценка KPI (KpiRecord, KpiCalculationService)");
        sources.put("overtime", "Переработки из табеля (TimesheetService / WorkloadRecord)");
        sources.put("absence", "Дни больничного и отпуска из табеля (TimesheetService, BR-030)");
        sources.put("missed1on1", "Пропущенные встречи 1:1 (OneOnOneMeeting.missed, FR-026)");
        sources.put("wellbeing", "Средняя оценка опросов LIKERT-5 (SurveyResponse, без анонимных)");
        return sources;
    }

    // ── Вспомогательные ──────────────────────────────────────────────────

    /**
     * Линейная нормализация значения в шкалу 0..100 с ограничением
     * (например, индекс нагрузки 1.3 → 0, 2.0 → 100).
     */
    private static double scale(double value, double min, double max) {
        if (max <= min) {
            return 0.0;
        }
        double ratio = (value - min) / (max - min);
        return round(clamp(ratio) * 100.0);
    }

    private static double clamp(double value) {
        return Math.min(1.0, Math.max(0.0, value));
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    // ── DTO ──────────────────────────────────────────────────────────────

    /**
     * Снимок «Радара выгорания» для дашборда.
     *
     * @param employees    профили видимых сотрудников (по убыванию балла)
     * @param teamAverages усреднённые значения факторов по видимой области
     * @param factorLabels подписи факторов в порядке радар-диаграммы
     * @param availableKeys ключи факторов, по которым есть данные
     * @param windowDays   окно анализа в днях
     */
    public record BurnoutRadarSnapshot(List<EmployeeBurnoutFactors> employees,
                                       List<FactorValue> teamAverages,
                                       List<Map.Entry<String, String>> factorLabels,
                                       Set<String> availableKeys,
                                       int windowDays) {

        /** Средний балл выгорания по видимой области; {@code null}, если факторов нет. */
        public Double teamScore() {
            List<Double> values = teamAverages.stream()
                    .map(FactorValue::value)
                    .filter(v -> v != null)
                    .toList();
            if (values.isEmpty()) {
                return null;
            }
            double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            return Math.round(average * 10.0) / 10.0;
        }

        /** Есть ли хоть какие-то данные для радара. */
        public boolean hasData() {
            return !availableKeys.isEmpty() && !employees.isEmpty();
        }

        public int employeeCount() {
            return employees.size();
        }

        /** Значение фактора по ключу либо {@code null}, если данных нет. */
        public Double value(String key) {
            for (FactorValue factor : teamAverages) {
                if (factor.key().equals(key)) {
                    return factor.value();
                }
            }
            return null;
        }
    }

    /**
     * Профиль выгорания одного сотрудника.
     *
     * @param score итоговый балл 0..100 (среднее по доступным факторам)
     */
    public record EmployeeBurnoutFactors(Long employeeId, String employeeName, String department,
                                         List<FactorValue> factors) {

        /** Итоговый балл 0..100; {@code 0.0}, если ни один фактор недоступен. */
        public double score() {
            return factors.stream()
                    .map(FactorValue::value)
                    .filter(v -> v != null)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
        }

        /** Округлённый балл для вывода. */
        public double scoreRounded() {
            return Math.round(score() * 10.0) / 10.0;
        }

        /**
         * Уровень риска выгорания (градация уровней проекта: LOW/MEDIUM/HIGH).
         *
         * <p>Если ни один фактор недоступен, возвращается {@code NO_DATA}:
         * отсутствие данных нельзя показывать как «низкий риск» — это разные
         * утверждения (у руководителей без записей нагрузки и KPI балл равен
         * нулю только потому, что считать нечего).
         */
        public String riskLevel() {
            if (availableFactorCount() == 0) {
                return "NO_DATA";
            }
            double s = score();
            if (s >= HIGH_RISK_THRESHOLD) {
                return "HIGH";
            }
            return s >= LOW_RISK_THRESHOLD ? "MEDIUM" : "LOW";
        }

        /** Количество доступных факторов (для честного «нет данных»). */
        public long availableFactorCount() {
            return factors.stream().filter(f -> f.value() != null).count();
        }

        /** Значение фактора по ключу либо {@code null}. */
        public Double value(String key) {
            for (FactorValue factor : factors) {
                if (factor.key().equals(key)) {
                    return factor.value();
                }
            }
            return null;
        }
    }

    /**
     * Значение одного фактора выгорания.
     *
     * @param key   машинный ключ фактора
     * @param label подпись для интерфейса
     * @param value вклад в выгорание 0..100 либо {@code null}, если данных нет
     */
    public record FactorValue(String key, String label, Double value) {

        public boolean available() {
            return value != null;
        }

        /** Значение для вывода: число либо прочерк при отсутствии данных. */
        public String display() {
            return value == null ? "—" : String.format("%.1f", value);
        }
    }
}
