package com.staffsense.demo.api.content;

import com.staffsense.demo.api.onboarding.PortalManageApiController;
import com.staffsense.demo.api.survey.SurveyBuilderApiController;
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
 * Перевод доменных отказов конструктора опросов и управления порталом в 409.
 *
 * <p>Почему отдельный совет, а не правка {@code GlobalControllerAdvice}: общий
 * обработчик ловит {@code Exception} и превращает незнакомое исключение в 500,
 * а трогать общий файл во время параллельной работы нельзя. Совет привязан
 * только к двум новым контроллерам и стоит первым в цепочке, поэтому
 * существующие эндпоинты он не задевает — тот же приём, что у
 * {@code AdminApiExceptionHandler}.
 *
 * <p>Конфликт состояния — это не «внутренняя ошибка»: попытка опубликовать
 * опрос без вопросов или отредактировать уже опубликованный должна объяснять
 * клиенту причину, а не отдавать 500 с текстом «внутренняя ошибка сервера».
 *
 * <p>Форма тела ошибки повторяет общую ({@code timestamp}, {@code status},
 * {@code error}, {@code message}, {@code path}), чтобы клиент разбирал её
 * единообразно.
 */
@RestControllerAdvice(assignableTypes = {
        SurveyBuilderApiController.class,
        PortalManageApiController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ContentApiExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex,
                                                              HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", HttpStatus.CONFLICT.getReasonPhrase());
        body.put("message", ex.getMessage() != null ? ex.getMessage() : "Действие недоступно в текущем статусе");
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
