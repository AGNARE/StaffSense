package com.staffsense.demo.dto.task;

import com.staffsense.demo.model.TaskPriority;
import com.staffsense.demo.model.TaskStatus;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskCreateRequest {
    @jakarta.validation.constraints.NotBlank
    private String title;
    private String description;
    private Long projectId;
    private Long assigneeId;
    private TaskPriority priority;
    private Double estimateHours;
    private LocalDate dueDate;
}
