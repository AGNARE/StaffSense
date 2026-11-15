package com.staffsense.demo.api.admin;

import com.staffsense.demo.exception.AdminOperationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Перевод доменных отказов администрирования в HTTP 409.
 *
 * <p>Почему отдельный совет, а не правка {@code GlobalControllerAdvice}: общий
 * обработчик ловит {@code Exception} и превращает незнакомое исключение в 500,
 * а трогать общий файл во время параллельной работы нельзя. Совет привязан
 * только к {@link AdminUserApiController} и стоит первым в цепочке, поэтому
 * остальные эндпоинты он не задевает.
 *
 * <p>Форма тела ошибки повторяет общую ({@code timestamp}, {@code status},
 * {@code error}, {@code message}, {@code path}), чтобы клиент разбирал её
 * единообразно.
 */
@RestControllerAdvice(assignableTypes = AdminUserApiController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminApiExceptionHandler {

    @ExceptionHandler(AdminOperationException.class)
    public ResponseEntity<Map<String, Object>> handleAdminOperation(
            AdminOperationException ex, HttpServletRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", HttpStatus.CONFLICT.getReasonPhrase());
        body.put("message", ex.getMessage());
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
