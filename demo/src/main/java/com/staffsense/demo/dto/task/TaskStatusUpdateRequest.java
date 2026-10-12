package com.staffsense.demo.dto.task;

import com.staffsense.demo.model.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusUpdateRequest {

    /** Без @NotNull {@code {"status":null}} приводил к нарушению NOT NULL в БД и ответу 500. */
    @NotNull(message = "Статус обязателен")
    private TaskStatus status;
}
