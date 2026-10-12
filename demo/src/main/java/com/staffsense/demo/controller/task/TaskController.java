package com.staffsense.demo.controller.task;

import com.staffsense.demo.dto.task.TaskCreateRequest;
import com.staffsense.demo.dto.task.TaskStatusUpdateRequest;
import com.staffsense.demo.model.Task;
import com.staffsense.demo.service.task.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST API задач.
 *
 * <p>Ранее не имел ни одной проверки прав: любой аутентифицированный
 * пользователь (включая EMPLOYEE) мог создать задачу в любом проекте и
 * сменить статус любой задачи. Дополнительно CSRF был отключён для /api/**.
 */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /** Создание задачи — только ADMIN и PM (матрица доступа, раздел 21 ТЗ). */
    @PostMapping("/new")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public ResponseEntity<Task> createTask(@Valid @RequestBody TaskCreateRequest request,
                                           Authentication auth) {
        return ResponseEntity.ok(taskService.createTask(request, auth));
    }

    /**
     * Смена статуса. Роль проверяется в сервисе по принадлежности задачи:
     * EMPLOYEE — только свои задачи, PM — свои проекты/команда, ADMIN — любые.
     */
    @PostMapping("/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Task> updateStatus(@PathVariable Long id,
                                             @Valid @RequestBody TaskStatusUpdateRequest request,
                                             Authentication auth) {
        return ResponseEntity.ok(taskService.updateStatus(id, request.getStatus(), auth));
    }
}
