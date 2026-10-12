package com.staffsense.demo.service.metrics;

import com.staffsense.demo.model.Task;
import com.staffsense.demo.model.TaskStatus;
import com.staffsense.demo.model.User;
import com.staffsense.demo.model.WorkloadRecord;
import com.staffsense.demo.repository.TaskRepository;
import com.staffsense.demo.repository.workload.WorkloadRecordRepository;
import com.staffsense.demo.service.TimesheetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Расчёт индекса нагрузки сотрудника.
 *
 * <p><b>Что исправлено относительно исходной версии:</b>
 * <ol>
 *   <li>сервис <b>не вызывался ниоткуда</b> — индекс нагрузки пересчитывался
 *       только при первичном сидировании;</li>
 *   <li>не учитывались границы периода (BR-008 / спецификация §6.1):
 *       задача, созданная после периода или завершённая до его начала,
 *       всё равно попадала в расчёт;</li>
 *   <li>{@code findAll()} по всем задачам заменён выборкой по исполнителю;</li>
 *   <li>{@code getCurrentWorkloadIndex} делил на ёмкость без защиты от нуля
 *       (давал Infinity/NaN).</li>
 * </ol>
 *
 * <p><b>Формула (BR-008):</b>
 * <pre>
 *   totalEstimatedHours = Σ estimateHours активных задач в периоде
 *   weeks               = max(1, недель между началом и концом периода)
 *   capacityHours       = weeklyCapacityHours × weeks
 *   workloadIndex       = totalEstimatedHours / capacityHours
 * </pre>
 * Категории (BR-002): &lt;0.6 недогруз, 0.6–1.0 норма, 1.0–1.3 повышенная, &gt;1.3 перегруз.
 */
@Service
public class WorkloadCalculationService {

    private static final Logger log = LoggerFactory.getLogger(WorkloadCalculationService.class);

    /** Порог критической перегрузки (BR-002). */
    public static final double OVERLOAD_THRESHOLD = 1.3;

    private final TaskRepository taskRepository;
    private final WorkloadRecordRepository workloadRecordRepository;
    private final TimesheetService timesheetService;

    public WorkloadCalculationService(TaskRepository taskRepository,
                                      WorkloadRecordRepository workloadRecordRepository,
                                      TimesheetService timesheetService) {
        this.taskRepository = taskRepository;
        this.workloadRecordRepository = workloadRecordRepository;
        this.timesheetService = timesheetService;
    }

    /**
     * Пересчёт индекса нагрузки за период. Повторный вызов за тот же период
     * обновляет существующую запись (upsert).
     */
    @Transactional
    public WorkloadRecord calculateAndSave(User user, LocalDate periodStart, LocalDate periodEnd) {
        List<Task> activeTasks = taskRepository
                .findByAssigneeIdAndStatusNot(user.getId(), TaskStatus.DONE).stream()
                .filter(t -> isActiveInPeriod(t, periodStart, periodEnd))
                .toList();

        double totalHours = activeTasks.stream()
                .mapToDouble(t -> t.getEstimateHours() != null ? t.getEstimateHours() : 0.0)
                .sum();

        long weeks = Math.max(1, ChronoUnit.WEEKS.between(periodStart, periodEnd));
        int weeklyCapacity = user.getWeeklyCapacityHours() != null ? user.getWeeklyCapacityHours() : 0;

        // BR-030: дни больничного и отпуска (из утверждённых служебных записок)
        // уменьшают доступную ёмкость периода.
        long absenceDays = timesheetService.countAbsenceDays(user.getId(), periodStart, periodEnd);
        double capacityHours = (double) weeklyCapacity * weeks
                - absenceDays * TimesheetService.ABSENCE_HOURS_PER_DAY;
        if (capacityHours < 0) {
            capacityHours = 0;
        }

        double workloadIndex = capacityHours > 0 ? totalHours / capacityHours : 0.0;

        WorkloadRecord record = workloadRecordRepository
                .findFirstByUserIdAndPeriodStartAndPeriodEnd(user.getId(), periodStart, periodEnd)
                .orElseGet(WorkloadRecord::new);

        record.setUser(user);
        record.setPeriodStart(periodStart);
        record.setPeriodEnd(periodEnd);
        record.setTotalEstimatedHours(totalHours);
        record.setCapacityHours(capacityHours);
        record.setWorkloadIndex(workloadIndex);

        WorkloadRecord saved = workloadRecordRepository.save(record);
        log.info("Workload for user {} period {}-{}: index={} ({}h / {}h)",
                user.getId(), periodStart, periodEnd, workloadIndex, totalHours, capacityHours);
        return saved;
    }

    /** Пересчёт за текущий календарный месяц. */
    @Transactional
    public WorkloadRecord recalculateCurrentMonth(User user) {
        LocalDate today = LocalDate.now();
        return calculateAndSave(user, today.withDayOfMonth(1),
                today.withDayOfMonth(today.lengthOfMonth()));
    }

    /**
     * Задача учитывается в периоде, если она создана не позже конца периода
     * и не завершена до его начала (спецификация §6.1).
     */
    private boolean isActiveInPeriod(Task task, LocalDate periodStart, LocalDate periodEnd) {
        if (task.getCreatedAt() != null && task.getCreatedAt().toLocalDate().isAfter(periodEnd)) {
            return false;
        }
        return task.getCompletedAt() == null
                || !task.getCompletedAt().toLocalDate().isBefore(periodStart);
    }

    public List<WorkloadRecord> getHistory(Long userId) {
        return workloadRecordRepository.findByUserIdOrderByPeriodEndDesc(userId);
    }

    /** Текущий индекс нагрузки: последняя запись либо «живой» расчёт. */
    public double getCurrentWorkloadIndex(User user) {
        List<WorkloadRecord> records = getHistory(user.getId());
        if (!records.isEmpty()) {
            return records.get(0).getWorkloadIndex();
        }
        int weeklyCapacity = user.getWeeklyCapacityHours() != null ? user.getWeeklyCapacityHours() : 0;
        if (weeklyCapacity <= 0) {
            return 0.0;
        }
        double hours = taskRepository
                .findByAssigneeIdAndStatusNot(user.getId(), TaskStatus.DONE).stream()
                .mapToDouble(t -> t.getEstimateHours() != null ? t.getEstimateHours() : 0.0)
                .sum();
        return hours / weeklyCapacity;
    }
}
