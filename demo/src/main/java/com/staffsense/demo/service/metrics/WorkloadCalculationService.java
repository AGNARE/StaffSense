package com.staffsense.demo.service.metrics;

import com.staffsense.demo.model.Task;
import com.staffsense.demo.model.TaskStatus;
import com.staffsense.demo.model.User;
import com.staffsense.demo.model.WorkloadRecord;
import com.staffsense.demo.repository.TaskRepository;
import com.staffsense.demo.repository.workload.WorkloadRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class WorkloadCalculationService {

    private static final Logger log = LoggerFactory.getLogger(WorkloadCalculationService.class);

    private final TaskRepository taskRepository;
    private final WorkloadRecordRepository workloadRecordRepository;

    public WorkloadCalculationService(TaskRepository taskRepository, WorkloadRecordRepository workloadRecordRepository) {
        this.taskRepository = taskRepository;
        this.workloadRecordRepository = workloadRecordRepository;
    }

    public WorkloadRecord calculateAndSave(User user, LocalDate periodStart, LocalDate periodEnd) {
        // Active tasks as of period (not DONE, or completed within period)
        List<Task> activeTasks = taskRepository.findAll().stream()
                .filter(t -> t.getAssignee() != null
                        && t.getAssignee().getId().equals(user.getId())
                        && t.getStatus() != TaskStatus.DONE)
                .toList();

        double totalHours = activeTasks.stream().mapToDouble(Task::getEstimateHours).sum();

        long weeks = Math.max(1, ChronoUnit.WEEKS.between(periodStart, periodEnd));
        double capacityHours = user.getWeeklyCapacityHours() * weeks;
        double workloadIndex = capacityHours > 0 ? totalHours / capacityHours : 0.0;

        WorkloadRecord record = new WorkloadRecord();
        record.setUser(user);
        record.setPeriodStart(periodStart);
        record.setPeriodEnd(periodEnd);
        record.setTotalEstimatedHours(totalHours);
        record.setCapacityHours(capacityHours);
        record.setWorkloadIndex(workloadIndex);

        return workloadRecordRepository.save(record);
    }

    public List<WorkloadRecord> getHistory(Long userId) {
        return workloadRecordRepository.findByUserIdOrderByPeriodEndDesc(userId);
    }

    public double getCurrentWorkloadIndex(User user) {
        List<WorkloadRecord> records = getHistory(user.getId());
        if (!records.isEmpty()) return records.get(0).getWorkloadIndex();
        // Calculate live
        List<Task> active = taskRepository.findAll().stream()
                .filter(t -> t.getAssignee() != null
                        && t.getAssignee().getId().equals(user.getId())
                        && t.getStatus() != TaskStatus.DONE)
                .toList();
        double hours = active.stream().mapToDouble(Task::getEstimateHours).sum();
        return hours / user.getWeeklyCapacityHours();
    }
}
