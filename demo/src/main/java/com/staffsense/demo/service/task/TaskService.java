package com.staffsense.demo.service.task;

import com.staffsense.demo.dto.task.TaskCreateRequest;
import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.Project;
import com.staffsense.demo.model.Task;
import com.staffsense.demo.model.TaskStatus;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.ProjectRepository;
import com.staffsense.demo.repository.TaskRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.TeamAccessService;
import com.staffsense.demo.service.metrics.KpiCalculationService;
import com.staffsense.demo.service.metrics.WorkloadCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TeamAccessService teamAccessService;
    private final KpiCalculationService kpiCalculationService;
    private final WorkloadCalculationService workloadCalculationService;

    public List<Task> getTasksByProject(Long projectId) {
        return taskRepository.findByProjectId(projectId);
    }

    /**
     * Создание задачи. Вызывающий должен быть ADMIN или PM (проверяется
     * на уровне контроллера), а также иметь права на конкретный проект:
     * PM — только в своих проектах (TeamAccessService.canCreateTaskIn).
     */
    @Transactional
    public Task createTask(TaskCreateRequest request, Authentication auth) {
        User actor = teamAccessService.requireCurrentUser(auth);

        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with ID: " + request.getProjectId()));

        teamAccessService.requireCanCreateTaskIn(actor, project);

        log.info("User {} creates task '{}' in project {}", actor.getEmail(), request.getTitle(), project.getId());

        Task.TaskBuilder taskBuilder = Task.builder()
                .project(project)
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority())
                .estimateHours(request.getEstimateHours())
                .dueDate(request.getDueDate())
                .status(TaskStatus.TODO)
                .reopenedCount(0);

        if (request.getAssigneeId() != null) {
            User assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + request.getAssigneeId()));
            taskBuilder.assignee(assignee);
        }

        Task saved = taskRepository.save(taskBuilder.build());
        recalculateMetrics(saved.getAssignee());
        return saved;
    }

    /**
     * Смена статуса задачи с проверкой прав: ADMIN — любую задачу,
     * PM — задачи своих проектов/команды, EMPLOYEE — только свои.
     */
    @Transactional
    public Task updateStatus(Long taskId, TaskStatus newStatus, Authentication auth) {
        User actor = teamAccessService.requireCurrentUser(auth);

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with ID: " + taskId));

        teamAccessService.requireCanModifyTask(actor, task);

        TaskStatus oldStatus = task.getStatus();
        log.info("User {} updates task {} status {} -> {}", actor.getEmail(), taskId, oldStatus, newStatus);

        if (newStatus == TaskStatus.DONE) {
            task.setCompletedAt(LocalDateTime.now());
        } else if (task.getCompletedAt() != null && oldStatus == TaskStatus.DONE) {
            // Возврат из DONE — фиксируем переоткрытие (BR-014).
            task.setCompletedAt(null);
            task.setReopenedCount(task.getReopenedCount() + 1);
            log.info("Task {} reopened. New reopened count: {}", taskId, task.getReopenedCount());
        }

        task.setStatus(newStatus);
        Task saved = taskRepository.save(task);

        // BR-001: изменение статуса задачи влияет на нагрузку и KPI исполнителя.
        // Ранее пересчёт не выполнялся вообще — метрики оставались «замороженными»
        // с момента сидирования.
        recalculateMetrics(saved.getAssignee());
        return saved;
    }

    /**
     * Пересчёт нагрузки и KPI исполнителя за текущий месяц.
     * Ошибка пересчёта не должна отменять изменение задачи, поэтому
     * исключение логируется, а не пробрасывается.
     */
    private void recalculateMetrics(User assignee) {
        if (assignee == null) {
            return;
        }
        try {
            workloadCalculationService.recalculateCurrentMonth(assignee);
        } catch (Exception e) {
            log.warn("Не удалось пересчитать нагрузку для пользователя {}: {}", assignee.getId(), e.getMessage());
        }
        try {
            kpiCalculationService.recalculateCurrentMonth(assignee);
        } catch (Exception e) {
            log.warn("Не удалось пересчитать KPI для пользователя {}: {}", assignee.getId(), e.getMessage());
        }
    }
}
