package com.staffsense.demo.api.task;

import com.staffsense.demo.model.Task;

import java.time.LocalDate;
import java.util.List;
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
        LocalDateTime completedAt,
        /**
         * Разрешённые переходы статуса (BR-014). Считаются на сервере и отдаются
         * клиенту, чтобы правило переходов не дублировалось в интерфейсе.
         */
        List<String> allowedTransitions
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
                task.getCompletedAt(),
                task.getStatus() == null
                        ? List.of()
                        : java.util.Arrays.stream(com.staffsense.demo.model.TaskStatus.values())
                                .filter(target -> target != task.getStatus()
                                        && task.getStatus().canTransitionTo(target))
                                .map(Enum::name)
                                .toList());
    }
}
