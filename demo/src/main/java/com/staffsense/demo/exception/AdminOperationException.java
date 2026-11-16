package com.staffsense.demo.exception;

/**
 * Административное действие нарушает правило предметной области.
 *
 * <p>Примеры: попытка заблокировать самого себя и попытка снять роль
 * ADMIN с последнего активного администратора. Это не ошибка валидации полей
 * (400 — «поле пустое»), а конфликт с текущим состоянием системы, поэтому
 * клиенту возвращается 409 и текст правила, чтобы интерфейс мог показать
 * причину отказа без догадок.
 */
public class AdminOperationException extends RuntimeException {

    public AdminOperationException(String message) {
        super(message);
    }
}
