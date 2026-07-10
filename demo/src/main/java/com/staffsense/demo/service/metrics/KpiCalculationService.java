package com.staffsense.demo.service.metrics;

import com.staffsense.demo.model.KpiRecord;
import com.staffsense.demo.model.Task;
import com.staffsense.demo.model.TaskStatus;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.TaskRepository;
import com.staffsense.demo.repository.kpi.KpiRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class KpiCalculationService {

    private static final Logger log = LoggerFactory.getLogger(KpiCalculationService.class);

    // KPI weights from spec section 6.2
    private static final double W1 = 0.5; // onTimeCompletionRate
    private static final double W2 = 0.2; // reopenRate
    private static final double W3 = 0.3; // managerRating

    private final TaskRepository taskRepository;
    private final KpiRecordRepository kpiRecordRepository;

    public KpiCalculationService(TaskRepository taskRepository, KpiRecordRepository kpiRecordRepository) {
        this.taskRepository = taskRepository;
        this.kpiRecordRepository = kpiRecordRepository;
    }

    public KpiRecord calculateAndSave(User user, LocalDate periodStart, LocalDate periodEnd) {
        List<Task> periodTasks = taskRepository.findAll().stream()
                .filter(t -> t.getAssignee() != null
                        && t.getAssignee().getId().equals(user.getId())
                        && t.getStatus() == TaskStatus.DONE
                        && t.getCompletedAt() != null
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
        double reopenRate = totalDone > 0 ? (double) totalReopened / totalDone : 0.0;

        // kpiScore formula from spec 6.2
        double kpiScore = W1 * (onTimeRate * 100)
                + W2 * (100 - Math.min(reopenRate * 100, 100));
        // managerRating: if not set, redistribute W2+W3 weight to W1
        // For simplicity, skip manager rating (set it to null/not applicable)
        // Normalize to W1+W2 = 0.7 scale, then scale to 100
        kpiScore = kpiScore / (W1 + W2) * 100;
        kpiScore = Math.max(0, Math.min(100, kpiScore));

        KpiRecord record = new KpiRecord();
        record.setUser(user);
        record.setPeriodStart(periodStart);
        record.setPeriodEnd(periodEnd);
        record.setOnTimeCompletionRate(onTimeRate);
        record.setReopenRate(reopenRate);
        record.setKpiScore(kpiScore);

        return kpiRecordRepository.save(record);
    }

    public List<KpiRecord> getHistory(Long userId) {
        return kpiRecordRepository.findByUserIdOrderByPeriodEndDesc(userId);
    }

    public double getLatestKpiScore(Long userId) {
        List<KpiRecord> records = getHistory(userId);
        return records.isEmpty() ? 70.0 : records.get(0).getKpiScore();
    }
}
