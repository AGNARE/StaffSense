package com.staffsense.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffsense.demo.config.RiskThresholdProperties;
import com.staffsense.demo.dto.PythonPredictRequest;
import com.staffsense.demo.dto.PythonPredictResponse;
import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.*;
import com.staffsense.demo.repository.kpi.KpiRecordRepository;
import com.staffsense.demo.repository.workload.WorkloadRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;

/**
 * ================================================================
 * ФАЙЛ:       AttritionMlService.java
 * ПУТЬ:       demo/src/main/java/com/staffsense/demo/service/
 * НАЗНАЧЕНИЕ: Мост между Java-приложением и Python ML-сервисом
 * ЧТО ДЕЛАЕТ: Собирает 7 признаков из БД → отправляет POST запрос
 *             в Python FastAPI (http://localhost:8001/api/v1/predict) →
 *             получает riskScore, riskLevel, actionPlan → сохраняет в БД.
 *
 * АРХИТЕКТУРА ВЗАИМОДЕЙСТВИЯ:
 *   ┌─────────────────────┐      POST /api/v1/predict      ┌──────────────────────┐
 *   │ AttritionMlService  │ ──────────────────────────────► │ Python FastAPI       │
 *   │ (Spring Boot)       │                                 │ (scikit-learn)       │
 *   │                     │◄────────────────────────────── │ LogisticRegression    │
 *   │ Сохраняет в БД      │      riskScore, riskLevel       │ обучена на dataset   │
 *   └─────────────────────┘                                 └──────────────────────┘
 *
 * Если Python-сервис недоступен → fallbackPrediction() (эвристика).
 *
 * ЛОГИСТИЧЕСКАЯ РЕГРЕССИЯ (формула, используемая Python-сервисом):
 *   logit      = W1*X1 + W2*X2 + ... + W7*X7 + B0
 *   probability = 1 / (1 + exp(-logit))    ← сигмоида
 *   riskScore  = probability * 100
 *   riskLevel: >= 66 → HIGH, >= 33 → MEDIUM, < 33 → LOW (пороги из risk.thresholds, BR-007)
 *
 * ГДЕ X1..X7 — 7 признаков (нормализованы 0..1):
 *   X1 = salary_ratio       — отношение зарплаты к рынку
 *   X2 = workload_index     — индекс нагрузки (часы / норма)
 *   X3 = kpi_norm           — KPI / 100
 *   X4 = tenure_months_norm — стаж в месяцах / 60
 *   X5 = satisfaction_norm  — удовлетворённость (LIKERT / 5)
 *   X6 = missed_1on1_ratio  — доля пропущенных 1:1
 *   X7 = overtime_norm      — переработки / (capacity * 0.2)
 * ================================================================
 */
@Service
public class AttritionMlService {

    private static final Logger log = LoggerFactory.getLogger(AttritionMlService.class);

    /** Сериализация снимка признаков и XAI-факторов; числа всегда с точкой. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${ml.service.url:http://localhost:8000/api/v1/predict}")
    private String mlServiceUrl;

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final KpiRecordRepository kpiRecordRepository;
    private final WorkloadRecordRepository workloadRecordRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final SurveyQuestionRepository surveyQuestionRepository;
    private final OneOnOneMeetingRepository oneOnOneMeetingRepository;
    private final AttritionPredictionRepository attritionPredictionRepository;
    private final RiskThresholdProperties riskThresholds;

    public AttritionMlService(
            RestTemplate restTemplate,
            UserRepository userRepository,
            TaskRepository taskRepository,
            KpiRecordRepository kpiRecordRepository,
            WorkloadRecordRepository workloadRecordRepository,
            SurveyAnswerRepository surveyAnswerRepository,
            SurveyResponseRepository surveyResponseRepository,
            SurveyQuestionRepository surveyQuestionRepository,
            OneOnOneMeetingRepository oneOnOneMeetingRepository,
            AttritionPredictionRepository attritionPredictionRepository,
            RiskThresholdProperties riskThresholds) {
        this.riskThresholds = riskThresholds;
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.kpiRecordRepository = kpiRecordRepository;
        this.workloadRecordRepository = workloadRecordRepository;
        this.surveyAnswerRepository = surveyAnswerRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.surveyQuestionRepository = surveyQuestionRepository;
        this.oneOnOneMeetingRepository = oneOnOneMeetingRepository;
        this.attritionPredictionRepository = attritionPredictionRepository;
    }

    public AttritionPrediction computeAndSave(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        PythonPredictRequest request = buildRequest(user);
        log.info("Calling ML service for userId={} at {}", userId, mlServiceUrl);

        PythonPredictResponse response;
        try {
            response = restTemplate.postForObject(mlServiceUrl, request, PythonPredictResponse.class);
        } catch (Exception e) {
            log.warn("ML service unavailable: {}. Using fallback heuristic.", e.getMessage());
            response = fallbackPrediction(request, userId);
        }

        return saveResult(user, request, response);
    }

    /**
     * Compute predictions for ALL active employees.
     * Used on first page load when no data exists yet.
     */
    public List<AttritionPrediction> computeAllEmployees() {
        List<User> employees = userRepository.findActiveByRole(true, Role.EMPLOYEE);
        List<AttritionPrediction> results = new ArrayList<>();
        for (User emp : employees) {
            try {
                results.add(computeAndSave(emp.getId()));
            } catch (Exception e) {
                log.error("Failed to compute prediction for userId={}: {}", emp.getId(), e.getMessage());
            }
        }
        log.info("Computed predictions for {}/{} employees", results.size(), employees.size());
        return results;
    }

    /**
     * Async version — runs in a background thread pool so it doesn't block
     * the HTTP request thread. The CompletableFuture allows the caller to
     * fire-and-forget or poll for completion.
     */
    @Async("mlTaskExecutor")
    public CompletableFuture<List<AttritionPrediction>> computeAllEmployeesAsync() {
        List<AttritionPrediction> results = computeAllEmployees();
        return CompletableFuture.completedFuture(results);
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     *  buildRequest — собирает 7 признаков для Python ML-модели
     * ═══════════════════════════════════════════════════════════════════════
     *
     *  Что происходит:
     *  1. Берём сотрудника из БД (User)
     *  2. Для каждого из 7 признаков вызываем соответствующий метод:
     *     - calculateSalaryRatio()       — отношение зарплаты к средней по грейду
     *     - calculateWorkloadIndex()     — индекс нагрузки (из WorkloadRecord или задач)
     *     - calculateKpiNorm()           — нормализованный KPI (из KpiRecord)
     *     - calculateTenureNorm()        — стаж (месяцы от hireDate)
     *     - calculateSatisfactionNorm()  — удовлетворённость (из опросов LIKERT)
     *     - calculateMissed1on1Ratio()   — доля пропущенных встреч 1:1
     *     - calculateOvertimeNorm()      — переработки (из WorkloadRecord)
     *  3. Упаковываем в PythonPredictRequest (JSON) и отправляем в Python
     *
     *  Все признаки нормализованы в диапазон [0, 1] — так требует
     *  LogisticRegression scikit-learn.
     *
     *  ВАЖНО: Эти вычисления дублируют логику DatasetGenerator.java,
     *  но используют РЕАЛЬНЫЕ данные из БД, а не случайные числа.
     * ═══════════════════════════════════════════════════════════════════════
     */
    private PythonPredictRequest buildRequest(User user) {
        PythonPredictRequest req = new PythonPredictRequest();
        req.setUserId(user.getId());
        req.setSalaryRatio(calculateSalaryRatio(user));
        req.setWorkloadIndex(calculateWorkloadIndex(user));
        req.setKpiNorm(calculateKpiNorm(user));
        req.setTenureMonthsNorm(calculateTenureNorm(user));
        req.setSatisfactionNorm(calculateSatisfactionNorm(user));
        req.setMissed1on1Ratio(calculateMissed1on1Ratio(user));
        req.setOvertimeNorm(calculateOvertimeNorm(user));
        return req;
    }

    // --- Feature Calculators ---

    private double calculateSalaryRatio(User user) {
        List<User> sameGrade = userRepository.findByGradeLevelAndActive(user.getGradeLevel(), true);
        if (sameGrade.isEmpty()) return 1.0;
        double avg = sameGrade.stream()
                .mapToDouble(u -> u.getSalary().doubleValue())
                .average().orElse(user.getSalary().doubleValue());
        return avg > 0 ? user.getSalary().doubleValue() / avg : 1.0;
    }

    private double calculateWorkloadIndex(User user) {
        List<WorkloadRecord> records = workloadRecordRepository.findByUserIdOrderByPeriodEndDesc(user.getId());
        if (!records.isEmpty()) {
            return records.get(0).getWorkloadIndex();
        }
        // Calculate on-the-fly from active tasks
        List<Task> activeTasks = taskRepository.findByAssigneeIdAndStatusNot(user.getId(), TaskStatus.DONE);
        double totalHours = activeTasks.stream().mapToDouble(Task::getEstimateHours).sum();
        double capacity = user.getWeeklyCapacityHours();
        return capacity > 0 ? totalHours / capacity : 1.0;
    }

    private double calculateKpiNorm(User user) {
        List<KpiRecord> records = kpiRecordRepository.findByUserIdOrderByPeriodEndDesc(user.getId());
        if (!records.isEmpty()) {
            return Math.min(records.get(0).getKpiScore() / 100.0, 1.0);
        }
        return 0.7; // default if no KPI recorded
    }

    private double calculateTenureNorm(User user) {
        long months = ChronoUnit.MONTHS.between(user.getHireDate(), LocalDate.now());
        return Math.min(months / 60.0, 1.0);
    }

    private double calculateSatisfactionNorm(User user) {
        // Average LIKERT scores from surveys answered by this user in last 90 days
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        List<SurveyResponse> responses = surveyResponseRepository.findByUserIdAndSubmittedAtAfter(user.getId(), cutoff);
        if (responses.isEmpty()) return 0.65;
        double totalScore = 0;
        int count = 0;
        for (SurveyResponse resp : responses) {
            List<SurveyAnswer> answers = surveyAnswerRepository.findByResponseIdAndLikertValueIsNotNull(resp.getId());
            for (SurveyAnswer ans : answers) {
                totalScore += ans.getLikertValue();
                count++;
            }
        }
        return count > 0 ? Math.min((totalScore / count) / 5.0, 1.0) : 0.65;
    }

    private double calculateMissed1on1Ratio(User user) {
        List<OneOnOneMeeting> meetings = oneOnOneMeetingRepository.findByEmployeeId(user.getId());
        if (meetings.isEmpty()) return 0.0;
        long missed = meetings.stream().filter(m -> Boolean.TRUE.equals(m.getMissed())).count();
        return (double) missed / meetings.size();
    }

    private double calculateOvertimeNorm(User user) {
        List<WorkloadRecord> records = workloadRecordRepository.findByUserIdOrderByPeriodEndDesc(user.getId());
        if (!records.isEmpty() && records.get(0).getOvertimeHours() != null) {
            double capacity = user.getWeeklyCapacityHours();
            double limit = capacity * 0.2;
            return limit > 0 ? Math.min(records.get(0).getOvertimeHours() / limit, 1.0) : 0.0;
        }
        return 0.0;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  FALLBACK — эвристика, когда Python ML-сервис недоступен
    // ═══════════════════════════════════════════════════════════════════════
    //
    //  Когда вызывается: если POST http://localhost:8001/api/v1/predict
    //  выбросил исключение (сервер не запущен, таймаут, ошибка).
    //
    //  Вместо логистической регрессии используется простая сумма баллов:
    //    - Базовый score = 50 (средний риск)
    //    - За каждое "опасное" значение признака добавляем штрафные баллы:
    //      * workloadIndex > 1.3 → +15 (критическая нагрузка)
    //      * salaryRatio < 0.9   → +10 (зарплата ниже рынка)
    //      * satisfaction < 0.5  → +10 (низкая удовлетворённость)
    //    - Максимум 95 (никогда не показываем 100% по эвристике)
    //
    //  ⚠️ Это ЗАМЕНА, а не альтернатива. Реальные предсказания делает
    //     только Python-сервис с обученной LogisticRegression.
    // ═══════════════════════════════════════════════════════════════════════

    private PythonPredictResponse fallbackPrediction(PythonPredictRequest req, Long userId) {
        // ── ШАГ 1: Начинаем с базового score = 50 ────────────────────
        // 50 — это "середина шкалы", означает умеренный риск.
        double score = 50.0;

        // ── ШАГ 2: Добавляем баллы за критические признаки ──────────
        // Каждое условие проверяет, вышел ли признак за безопасный порог.
        if (req.getWorkloadIndex() > 1.3) score += 15;   // перегрузка
        if (req.getSalaryRatio() < 0.9) score += 10;    // низкая зарплата
        if (req.getSatisfactionNorm() < 0.5) score += 10; // недовольство

        // ── ШАГ 3: Ограничиваем максимум 95 —─────────────────────────
        // Никогда не показываем 100%, чтобы не вводить в заблуждение.
        score = Math.min(score, 95.0);

        // ── ШАГ 4: Формируем ответ ──────────────────────────────────
        PythonPredictResponse resp = new PythonPredictResponse();
        resp.setUserId(userId);
        resp.setRiskScore(score);
        // BR-007: пороги берутся из risk.thresholds (application.yml), а не из
        // захардкоженных 33/66 — иначе изменение порога расходилось с ML-сервисом.
        resp.setRiskLevel(riskThresholds.levelFor(score).name());
        resp.setAiActionPlan("ML-сервис временно недоступен. Оценка на основе встроенной эвристики. Рекомендуется повторить расчёт позже.");
        java.util.Map<String, Object> snapshot = new java.util.HashMap<>();
        snapshot.put("salary_ratio", req.getSalaryRatio());
        snapshot.put("workload_index", req.getWorkloadIndex());
        snapshot.put("kpi_norm", req.getKpiNorm());
        snapshot.put("tenure_months_norm", req.getTenureMonthsNorm());
        snapshot.put("satisfaction_norm", req.getSatisfactionNorm());
        snapshot.put("missed_1on1_ratio", req.getMissed1on1Ratio());
        snapshot.put("overtime_norm", req.getOvertimeNorm());
        resp.setFeaturesSnapshot(snapshot);
        resp.setTopRiskFactors(List.of());
        return resp;
    }

    private AttritionPrediction saveResult(User user, PythonPredictRequest req, PythonPredictResponse resp) {
        // BR-010: у сотрудника хранится ОДИН актуальный прогноз, прежние удаляются.
        // Спецификация §4.11 и открытый вопрос Q-007 предполагали хранение истории
        // и тренда риска, однако по решению заказчика оставлено поведение BR-010
        // (история не ведётся). Если решение изменится — удалять записи не нужно,
        // достаточно перейти на выборку последнего прогноза (она уже используется:
        // findFirstByUserIdOrderByComputedAtDesc).
        attritionPredictionRepository.findByUserId(user.getId())
                .forEach(attritionPredictionRepository::delete);

        AttritionPrediction prediction = new AttritionPrediction();
        prediction.setUser(user);
        prediction.setRiskScore(resp.getRiskScore());
        prediction.setRiskLevel(RiskLevel.valueOf(resp.getRiskLevel()));
        prediction.setAiActionPlan(resp.getAiActionPlan());

        // Снимок признаков берём из ответа ML: там присутствует z_score (логит),
        // который ранее терялся, потому что JSON собирался вручную из запроса.
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (resp.getFeaturesSnapshot() != null && !resp.getFeaturesSnapshot().isEmpty()) {
            snapshot.putAll(resp.getFeaturesSnapshot());
        } else {
            snapshot.put("salary_ratio", req.getSalaryRatio());
            snapshot.put("workload_index", req.getWorkloadIndex());
            snapshot.put("kpi_norm", req.getKpiNorm());
            snapshot.put("tenure_months_norm", req.getTenureMonthsNorm());
            snapshot.put("satisfaction_norm", req.getSatisfactionNorm());
            snapshot.put("missed_1on1_ratio", req.getMissed1on1Ratio());
            snapshot.put("overtime_norm", req.getOvertimeNorm());
        }
        prediction.setFeaturesSnapshotJson(toJson(snapshot));

        // XAI: объяснение прогноза сохраняем, чтобы показать на странице риска.
        prediction.setTopRiskFactorsJson(toJson(resp.getTopRiskFactors()));

        AttritionPrediction saved = attritionPredictionRepository.save(prediction);
        log.info("Saved prediction for userId={}: score={}, level={}", user.getId(), resp.getRiskScore(), resp.getRiskLevel());
        return saved;
    }

    /** JSON-сериализация значения; при ошибке возвращает null и пишет WARN. */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Не удалось сериализовать значение в JSON: {}", e.getMessage());
            return null;
        }
    }
}
