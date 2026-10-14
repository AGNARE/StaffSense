package com.staffsense.demo.exception;

import com.staffsense.demo.model.TaskStatus;

/**
 * Недопустимый переход статуса задачи (FR-010, BR-014).
 *
 * <p>Маршрут задачи: TODO → IN_PROGRESS → REVIEW → DONE; возврат — на шаг назад.
 * До этой проверки можно было перескочить из TODO сразу в DONE или вернуть
 * готовую задачу в проверку, минуя маршрут, — из-за чего ломались completedAt
 * и reopenedCount, а они питают расчёт KPI (BR-009).
 */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(TaskStatus from, TaskStatus to) {
        super("Недопустимый переход статуса: «" + title(from) + "» → «" + title(to) + "». "
              + "Допустимо: " + allowed(from) + ".");
    }

    private static String title(TaskStatus status) {
        if (status == null) {
            return "не задан";
        }
        return switch (status) {
            case TODO -> "К выполнению";
            case IN_PROGRESS -> "В работе";
            case REVIEW -> "На проверке";
            case DONE -> "Готово";
        };
    }

    private static String allowed(TaskStatus from) {
        if (from == null) {
            return "любой статус";
        }
        return switch (from) {
            case TODO -> "«В работе»";
            case IN_PROGRESS -> "«К выполнению», «На проверке»";
            case REVIEW -> "«В работе», «Готово»";
            case DONE -> "«В работе» (переоткрытие задачи)";
        };
    }
}
