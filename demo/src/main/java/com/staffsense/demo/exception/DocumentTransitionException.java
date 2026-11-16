package com.staffsense.demo.exception;

import com.staffsense.demo.model.DocumentStatus;

/**
 * Нарушение маршрута документа: переход, которого нет в
 * {@link DocumentStatus#canTransitionTo}.
 *
 * <p>Отдельный тип, а не {@link IllegalStateException}: контроллер документов
 * переводит его в 409, чтобы клиент показал «недопустимый переход», а не
 * «внутренняя ошибка сервера». Права роли проверяются отдельно и дают 403 —
 * «нельзя этому пользователю» и «нельзя по маршруту» это разные ошибки.
 */
public class DocumentTransitionException extends RuntimeException {

    public DocumentTransitionException(String message) {
        super(message);
    }
}
