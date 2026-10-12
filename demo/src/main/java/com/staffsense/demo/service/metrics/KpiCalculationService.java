package com.staffsense.demo.service.metrics;

import com.staffsense.demo.model.KpiRecord;
import com.staffsense.demo.model.Task;
import com.staffsense.demo.model.TaskStatus;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.TaskRepository;
import com.staffsense.demo.repository.kpi.KpiRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Расчёт KPI сотрудника за период.
 *
 * <p><b>Что исправлено относительно исходной версии:</b>
 * <ol>
 *   <li>сервис <b>не вызывался ниоткуда</b> — KPI в базе появлялись только при
 *       первичном сидировании и никогда не пересчитывались;</li>
 *   <li>веса были константами, хотя {@code kpi.weights.*} есть в
 *       {@code application.yml} (теперь читаются из конфигурации);</li>
 *   <li>вес {@code manager-rating = 0.3} был объявлен, но не использовался —
 *       оценка руководителя теперь участвует в формуле, если задана;</li>
 *   <li>{@code findAll()} по всем задачам с фильтрацией в памяти заменён
 *       выборкой задач конкретного исполнителя.</li>
 * </ol>
 *
 * <p><b>Формула (BR-009, Q-020):</b>
 * <pre>
 *   onTimeRate  = задачи, завершённые не позже dueDate / все завершённые за период
 *   reopenRate  = сумма reopenedCount / число завершённых задач
 *
 *   если managerRating задан (1..5):
 *     managerNorm = (managerRating - 1) / 4 * 100
 *     kpi = (w1*onTimeRate*100 + w2*(100 - reopenRate*100) + w3*managerNorm) / (w1+w2+w3)
 *
 *   если не задан (вес w3 перераспределяется на w1 и w2 пропорционально):
 *     kpi = (w1*onTimeRate*100 + w2*(100 - reopenRate*100)) / (w1+w2)
 * </pre>
 * Вторая ветка в точности воспроизводит прежнюю формулу, поэтому поведение
 * для сотрудников без оценки руководителя не изменилось.
 */
@Service
public class KpiCalculationService {

    private static final Logger log = LoggerFactory.getLogger(KpiCalculationService.class);

    private final TaskRepository taskRepository;
    private final KpiRecordRepository kpiRecordRepository;

    private final double wOnTime;
    private final double wReopen;
    private final double wManagerRating;

    public KpiCalculationService(TaskRepository taskRepository,
                                 KpiRecordRepository kpiRecordRepository,
                                 @Value("${kpi.weights.on-time:0.5}") double wOnTime,
                                 @Value("${kpi.weights.reopen:0.2}") double wReopen,
                                 @Value("${kpi.weights.manager-rating:0.3}") double wManagerRating) {
        this.taskRepository = taskRepository;
        this.kpiRecordRepository = kpiRecordRepository;
        this.wOnTime = wOnTime;
        this.wReopen = wReopen;
        this.wManagerRating = wManagerRating;
    }

    /** Пересчёт без оценки руководителя (сохраняется ранее выставленная оценка, если была). */
    @Transactional
    public KpiRecord calculateAndSave(User user, LocalDate periodStart, LocalDate periodEnd) {
        return calculateAndSave(user, periodStart, periodEnd, null);
    }

    /**
     * Пересчёт KPI за период. Повторный вызов за тот же период обновляет
     * существующую запись (upsert), а не создаёт дубликат.
     */
    @Transactional
    public KpiRecord calculateAndSave(User user, LocalDate periodStart, LocalDate periodEnd,
                                      Double managerRating) {
        List<Task> periodTasks = taskRepository
                .findByAssigneeIdAndStatus(user.getId(), TaskStatus.DONE).stream()
                .filter(t -> t.getCompletedAt() != null
                        && !t.getCompletedAt().toLocalDate().isBefore(periodStart)
                        && !t.getCompletedAt().toLocalDate().isAfter(periodEnd))
                .toList();

        int totalDone = periodTasks.size();
        if (totalDone == 0) {
            log.info("No completed tasks for user {} in period {}-{}", user.getId(), periodStart, periodEnd);
            return null;
        }

        long onTime = periodTasks.stream()
                .filter(t -> t.getDueDate() != null
                        && !t.getCompletedAt().toLocalDate().isAfter(t.getDueDate()))
                .count();
        double onTimeRate = (double) onTime / totalDone;

        int totalReopened = periodTasks.stream().mapToInt(Task::getReopenedCount).sum();
        double reopenRate = (double) totalReopened / totalDone;

        KpiRecord record = kpiRecordRepository
                .findFirstByUserIdAndPeriodStartAndPeriodEnd(user.getId(), periodStart, periodEnd)
                .orElseGet(KpiRecord::new);

        // Если новая оценка не передана, сохраняем ранее выставленную.
        Double effectiveRating = managerRating != null ? managerRating : record.getManagerRating();

        double kpiScore = computeKpiScore(onTimeRate, reopenRate, effectiveRating);

        record.setUser(user);
        record.setPeriodStart(periodStart);
        record.setPeriodEnd(periodEnd);
        record.setOnTimeCompletionRate(onTimeRate);
        record.setReopenRate(reopenRate);
        record.setManagerRating(effectiveRating);
        record.setKpiScore(kpiScore);

        KpiRecord saved = kpiRecordRepository.save(record);
        log.info("KPI for user {} period {}-{}: score={} (onTime={}, reopen={}, managerRating={})",
                user.getId(), periodStart, periodEnd, kpiScore, onTimeRate, reopenRate, effectiveRating);
        return saved;
    }

    /** Пересчёт KPI за текущий календарный месяц. */
    @Transactional
    public KpiRecord recalculateCurrentMonth(User user) {
        LocalDate today = LocalDate.now();
        return calculateAndSave(user, today.withDayOfMonth(1),
                today.withDayOfMonth(today.lengthOfMonth()));
    }

    /**
     * Формула KPI. Вынесена отдельно, чтобы её можно было покрыть тестом
     * без обращения к базе.
     */
    double computeKpiScore(double onTimeRate, double reopenRate, Double managerRating) {
        double onTimeComponent = onTimeRate * 100.0;
        double reopenComponent = 100.0 - Math.min(reopenRate * 100.0, 100.0);

        double score;
        if (managerRating != null) {
            double clamped = Math.max(1.0, Math.min(5.0, managerRating));
            double managerComponent = (clamped - 1.0) / 4.0 * 100.0;
            double weightSum = wOnTime + wReopen + wManagerRating;
            if (weightSum <= 0) {
                return 0.0;
            }
            score = (wOnTime * onTimeComponent
                    + wReopen * reopenComponent
                    + wManagerRating * managerComponent) / weightSum;
        } else {
            double base = wOnTime + wReopen;
            if (base <= 0) {
                return 0.0;
            }
            score = (wOnTime * onTimeComponent + wReopen * reopenComponent) / base;
        }
        return Math.max(0.0, Math.min(100.0, score));
    }

    public List<KpiRecord> getHistory(Long userId) {
        return kpiRecordRepository.findByUserIdOrderByPeriodEndDesc(userId);
    }

    public double getLatestKpiScore(Long userId) {
        List<KpiRecord> records = getHistory(userId);
        return records.isEmpty() ? 70.0 : records.get(0).getKpiScore();
    }
}
