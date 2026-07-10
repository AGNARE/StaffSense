package com.staffsense.demo.controller.task;

import com.staffsense.demo.dto.task.TaskCreateRequest;
import com.staffsense.demo.dto.task.TaskStatusUpdateRequest;
import com.staffsense.demo.model.Project;
import com.staffsense.demo.model.Task;
import com.staffsense.demo.model.TaskStatus;
import com.staffsense.demo.service.task.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tasks") // Using API prefix for task operations
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping("/new")
    public ResponseEntity<Task> createTask(@Valid @RequestBody TaskCreateRequest request) {
        return ResponseEntity.ok(taskService.createTask(request));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<Task> updateStatus(@PathVariable Long id, @Valid @RequestBody TaskStatusUpdateRequest request) {
        return ResponseEntity.ok(taskService.updateStatus(id, request.getStatus()));
    }
}
