package com.staffsense.demo.api.hr;

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
 * Доменные отказы HR-модуля в HTTP 409.
 *
 * <p><b>Зачем отдельный совет.</b> {@link com.staffsense.demo.config.GlobalControllerAdvice}
 * ловит {@code Exception} и превращает незнакомое исключение в 500. Правка
 * закрытый период (BR-028) — это не ошибка сервера, а конфликт состояния:
 * {@code TimesheetService#saveEntry} бросает {@code IllegalStateException}.
 * Отдавать на него 500 значит соврать клиенту: запрос корректен, действие
 * запрещено правилом.
 *
 * <p>Совет привязан только к {@link HrApiController} и стоит первым в цепочке,
 * поэтому остальные эндпоинты (и их 500 на {@code IllegalStateException}) он
 * не задевает. Тело ошибки повторяет общую форму
 * ({@code timestamp}, {@code status}, {@code error}, {@code message},
 * {@code path}), чтобы клиент разбирал её единообразно.
 */
@RestControllerAdvice(assignableTypes = HrApiController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HrApiExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleStateConflict(IllegalStateException ex,
                                                                   HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", HttpStatus.CONFLICT.getReasonPhrase());
        body.put("message", ex.getMessage() != null ? ex.getMessage() : "Действие запрещено текущим состоянием");
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
