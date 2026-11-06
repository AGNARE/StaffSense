package com.staffsense.demo.api.memo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO служебных записок (FR-013…FR-017, BR-003, BR-029).
 *
 * <p>Маршрут согласования: черновик → виза руководителя → утверждение HR,
 * отклонение на любом шаге, отзыв автором до финального решения.
 */
public final class MemoApiDtos {

    private MemoApiDtos() {
    }

    /** Строка списка и карточка записки. */
    public record Memo(
            Long id,
            Long userId,
            String employeeName,
            String department,
            String type,
            String typeTitle,
            LocalDate startDate,
            LocalDate endDate,
            long days,
            String reason,
            String status,
            String statusTitle,
            LocalDateTime createdAt,
            String pmDecisionBy,
            LocalDateTime pmDecisionAt,
            String hrDecisionBy,
            LocalDateTime hrDecisionAt,
            String rejectionReason,
            /**
             * Что можно сделать с записью именно этому пользователю. Считается
             * на сервере: маршрут согласования не должен дублироваться в клиенте.
             */
            List<String> allowedActions
    ) {
    }

    /** Тело запроса на создание записки. */
    public record CreateRequest(
            @jakarta.validation.constraints.NotNull(message = "Тип записки обязателен")
            com.staffsense.demo.model.MemoType type,

            @jakarta.validation.constraints.NotNull(message = "Дата начала обязательна")
            LocalDate startDate,

            @jakarta.validation.constraints.NotNull(message = "Дата окончания обязательна")
            LocalDate endDate,

            @jakarta.validation.constraints.Size(max = 1000, message = "Причина слишком длинная")
            String reason
    ) {
    }

    /** Тело запроса на отклонение. */
    public record RejectRequest(
            @jakarta.validation.constraints.NotBlank(message = "Укажите причину отклонения")
            @jakarta.validation.constraints.Size(max = 500, message = "Причина слишком длинная")
            String reason
    ) {
    }
}
