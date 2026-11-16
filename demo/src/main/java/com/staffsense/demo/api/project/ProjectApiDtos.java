package com.staffsense.demo.api.project;

import com.staffsense.demo.api.task.TaskDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO контракта {@code /api/v1/projects}.
 *
 * <p>Третий модуль в API-first контуре и первый, где есть не только чтение:
 * создание задачи и смена статуса. Сущности наружу не отдаются — только
 * плоские DTO, чтобы ленивые связи не утекали в JSON.
 */
public final class ProjectApiDtos {

    private ProjectApiDtos() {
    }

    /**
     * Счётчики задач по статусам и производные от них значения.
     *
     * <p>{@code total} и {@code progressPercent} — компоненты записи, а не
     * вычисляемые методы: Jackson сериализует только компоненты, и когда это
     * были методы, клиент получал {@code undefined} вместо прогресса.
     */
    public record Counts(long todo, long inProgress, long review, long done,
                         long total, int progressPercent) {

        public static Counts of(long todo, long inProgress, long review, long done) {
            long all = todo + inProgress + review + done;
            int percent = all == 0 ? 0 : (int) Math.round(done * 100.0 / all);
            return new Counts(todo, inProgress, review, done, all, percent);
        }
    }

    /** Карточка проекта в списке. */
    public record ProjectCard(
            Long id,
            String name,
            String description,
            String ownerName,
            LocalDateTime createdAt,
            Counts counts
    ) {
    }

    /**
     * Тело запроса на создание задачи во вложенном маршруте.
     *
     * <p>Отдельный тип, а не {@code TaskCreateRequest}: там {@code projectId}
     * помечен {@code @NotNull}, и валидация срабатывает раньше, чем контроллер
     * успевает подставить проект из пути. Здесь проект задаёт URL — в теле его
     * быть не должно, иначе клиент мог бы прислать два разных проекта.
     */
    public record CreateTaskRequest(
            @jakarta.validation.constraints.NotBlank(message = "Название задачи обязательно")
            @jakarta.validation.constraints.Size(max = 200, message = "Название не должно превышать 200 символов")
            String title,

            @jakarta.validation.constraints.Size(max = 5000, message = "Описание слишком длинное")
            String description,

            Long assigneeId,
            com.staffsense.demo.model.TaskPriority priority,

            @jakarta.validation.constraints.Positive(message = "Оценка трудозатрат должна быть положительной")
            Double estimateHours,

        java.time.LocalDate dueDate,

        /**
         * Плановая дата старта. Необязательна: без неё план-график показывает
         * факт (дату создания), а строка подписана «план не задан».
         */
        java.time.LocalDate startDate
    ) {
    }

    /** Доска проекта: проект и его задачи. */
    public record Board(
            Long id,
            String name,
            String description,
            String ownerName,
            LocalDateTime createdAt,
            Counts counts,
            List<TaskDto> tasks
    ) {
    }
}
