package com.staffsense.demo.api.task;

import com.staffsense.demo.model.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO задачи для AJAX-эндпоинтов канбан-доски.
 *
 * <p>Зачем понадобилось: {@code TaskController} возвращал сущность {@link Task}
 * напрямую. Из-за этого в OpenAPI-схему попадали {@code Task}, {@code Project} и
 * {@code User} со всеми полями, ленивые связи могли дать
 * {@code LazyInitializationException}, а любое изменение сущности молча меняло
 * публичный формат ответа.
 *
 * <p>Клиенту (перетаскивание на доске) тело ответа не нужно вовсе — он смотрит
 * только на HTTP-статус и сообщение об ошибке. Поэтому формат можно было
 * сузить без риска: здесь только то, что осмысленно для внешнего потребителя.
 */
public record TaskDto(
        Long id,
        String title,
        String status,
        String priority,
        Long projectId,
        Long assigneeId,
        String assigneeName,
        Double estimateHours,
        Double actualHours,
        LocalDate dueDate,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {

    /** @param task сущность; связи берутся только «плоскими» идентификаторами */
    public static TaskDto from(Task task) {
        if (task == null) {
            return null;
        }
        return new TaskDto(
                task.getId(),
                task.getTitle(),
                task.getStatus() != null ? task.getStatus().name() : null,
                task.getPriority() != null ? task.getPriority().name() : null,
                task.getProject() != null ? task.getProject().getId() : null,
                task.getAssignee() != null ? task.getAssignee().getId() : null,
                task.getAssignee() != null ? task.getAssignee().getFullName() : null,
                task.getEstimateHours(),
                task.getActualHours(),
                task.getDueDate(),
                task.getCreatedAt(),
                task.getCompletedAt());
    }
}
