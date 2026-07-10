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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public List<Task> getTasksByProject(Long projectId) {
        return taskRepository.findByProjectId(projectId);
    }

    @Transactional
    public Task createTask(TaskCreateRequest request) {
        log.info("Creating new task: {}", request.getTitle());
        
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with ID: " + request.getProjectId()));

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

        return taskRepository.save(taskBuilder.build());
    }

    @Transactional
    public Task updateStatus(Long taskId, TaskStatus newStatus) {
        log.info("Updating task {} status to {}", taskId, newStatus);
        
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with ID: " + taskId));

        TaskStatus oldStatus = task.getStatus();

        // Handle logic for status transitions
        if (newStatus == TaskStatus.DONE) {
            task.setCompletedAt(LocalDateTime.now());
        } else if (task.getCompletedAt() != null && oldStatus == TaskStatus.DONE) {
            // If it was DONE and now it's something else, it's a reopen
            task.setCompletedAt(null);
            task.setReopenedCount(task.getReopenedCount() + 1);
            log.info("Task {} reopened. New reopened count: {}", taskId, task.getReopenedCount());
        }

        task.setStatus(newStatus);
        return taskRepository.save(task);
    }
}
