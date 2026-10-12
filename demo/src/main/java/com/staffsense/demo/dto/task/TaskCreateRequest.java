package com.staffsense.demo.dto.task;

import com.staffsense.demo.model.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = "Название задачи обязательно")
    @Size(max = 200, message = "Название не должно превышать 200 символов")
    private String title;

    @Size(max = 5000, message = "Описание слишком длинное")
    private String description;

    /** Ранее поле было nullable: null уходил в findById(null) → 500 вместо 400. */
    @NotNull(message = "Проект обязателен")
    private Long projectId;

    private Long assigneeId;

    private TaskPriority priority;

    @Positive(message = "Оценка трудозатрат должна быть положительной")
    private Double estimateHours;

    private LocalDate dueDate;
}
