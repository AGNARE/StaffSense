package com.staffsense.demo.config;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Единая обработка ошибок.
 *
 * <p>Ключевое отличие от исходной версии: для REST-запросов ({@code /api/**}
 * или {@code Accept: application/json}) возвращается JSON, а не HTML-страница
 * {@code error}. Ранее ошибка валидации в {@code @RestController} перехватывалась
 * {@code @ExceptionHandler(Exception.class)} и клиент получал 500 + HTML вместо
 * 400 + JSON.
 */
@ControllerAdvice
public class GlobalControllerAdvice {

    private static final Logger log = LoggerFactory.getLogger(GlobalControllerAdvice.class);

    private final UserRepository userRepository;

    public GlobalControllerAdvice(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @ModelAttribute("requestURI")
    public String getRequestURI(HttpServletRequest request) {
        return request.getRequestURI();
    }

    /**
     * Текущий пользователь для всех шаблонов, включая страницу ошибки.
     *
     * <p>Раньше {@code currentUser} клал в модель каждый контроллер вручную.
     * Страницы ошибки рендерятся вообще без контроллера ({@code /error} —
     * {@code BasicErrorController}, а также результат {@code @ExceptionHandler}),
     * поэтому в модели его не было, и {@code fragments/layout} падал на
     * {@code currentUser.role.name()} с {@code SpelEvaluationException}.
     *
     * <p>Контроллеры по-прежнему добавляют {@code currentUser} сами: атрибут
     * обработчика перекрывает значение из advice, поведение страниц не меняется.
     * Возвращаем {@code null} для анонимных запросов — шаблоны это учитывают.
     */
    @ModelAttribute("currentUser")
    public User currentUser(Authentication authentication) {
        return resolveCurrentUser(authentication);
    }

    /**
     * Имя роли текущего пользователя — безопасная замена {@code currentUser.role.name()}
     * в шаблонах.
     *
     * <p>Обращение к цепочке {@code currentUser.role.name()} падает, если
     * {@code currentUser} не попал в модель (страница ошибки) или пользователь
     * анонимный. Возвращаем пустую строку: сравнения {@code currentRole == 'ADMIN'}
     * при этом просто дают {@code false}, а рендер не падает.
     */
    @ModelAttribute("currentRole")
    public String currentRole(Authentication authentication) {
        return roleName(resolveCurrentUser(authentication));
    }

    /** {@code ""} вместо {@code null}: пустая роль не совпадает ни с одной реальной ролью. */
    private static String roleName(User user) {
        return user != null && user.getRole() != null ? user.getRole().name() : "";
    }

    /** {@code null}, если пользователь анонимный или БД недоступна: страница ошибки важнее. */
    private User resolveCurrentUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return userRepository.findByEmail(authentication.getName()).orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Не удалось определить текущего пользователя {}: {}",
                    authentication.getName(), ex.getMessage());
            return null;
        }
    }

    // ── 404 — Resource not found ──────────────────────────────────────
    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
    public Object handleNotFound(Exception ex, HttpServletRequest request) {
        if (isApiRequest(request)) {
            return apiError(HttpStatus.NOT_FOUND, "Ресурс не найден", request);
        }
        return errorView(HttpStatus.NOT_FOUND.value(), "Страница не найдена",
                "Запрашиваемый ресурс не существует или был удалён.");
    }

    // ── 403 — Access denied ───────────────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        if (isApiRequest(request)) {
            return apiError(HttpStatus.FORBIDDEN,
                    ex.getMessage() != null ? ex.getMessage() : "Доступ запрещён", request);
        }
        return errorView(HttpStatus.FORBIDDEN.value(), "Доступ запрещён",
                "У вас нет прав для просмотра этой страницы.");
    }

    // ── 400 — Ошибки валидации тела запроса (@Valid) ───────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Некорректные данные запроса.";
        }
        if (isApiRequest(request)) {
            return apiError(HttpStatus.BAD_REQUEST, message, request);
        }
        return errorView(HttpStatus.BAD_REQUEST.value(), "Неверный запрос", message);
    }

    // ── 400 — Нечитаемое тело запроса (битый JSON, неизвестный enum) ────
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Object handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String message = "Некорректный формат тела запроса.";
        if (isApiRequest(request)) {
            return apiError(HttpStatus.BAD_REQUEST, message, request);
        }
        return errorView(HttpStatus.BAD_REQUEST.value(), "Неверный запрос", message);
    }

    // ── 400 — Bad request (IllegalArgument) ──────────────────────────
    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Некорректные данные запроса.";
        if (isApiRequest(request)) {
            return apiError(HttpStatus.BAD_REQUEST, message, request);
        }
        return errorView(HttpStatus.BAD_REQUEST.value(), "Неверный запрос", message);
    }

    // ── 400 — Некорректная дата/время в параметрах формы ─────────────
    /** DateTimeParseException не наследуется от IllegalArgumentException, поэтому
     *  ранее «кривая» дата приводила к 500 вместо понятного 400. */
    @ExceptionHandler(DateTimeParseException.class)
    public Object handleDateTimeParse(DateTimeParseException ex, HttpServletRequest request) {
        String message = "Некорректный формат даты или времени.";
        if (isApiRequest(request)) {
            return apiError(HttpStatus.BAD_REQUEST, message, request);
        }
        return errorView(HttpStatus.BAD_REQUEST.value(), "Неверный запрос", message);
    }

    // ── 409 — Нарушение целостности данных (уникальность, NOT NULL, FK) ─
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation for URI {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        String message = "Данные конфликтуют с текущим состоянием базы (дубликат или отсутствующая связь).";
        if (isApiRequest(request)) {
            return apiError(HttpStatus.CONFLICT, message, request);
        }
        return errorView(HttpStatus.CONFLICT.value(), "Конфликт данных", message);
    }

    // ── 500 — Unexpected errors ──────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public Object handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for URI {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        if (isApiRequest(request)) {
            return apiError(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера", request);
        }
        return errorView(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Внутренняя ошибка сервера",
                "Произошла непредвиденная ошибка. Пожалуйста, попробуйте позже.");
    }

    /** REST-запрос: путь под /api/ либо клиент просит JSON. */
    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) {
            return true;
        }
        String accepts = request.getHeader("Accept");
        return accepts != null && accepts.contains("application/json");
    }

    private ResponseEntity<Map<String, Object>> apiError(HttpStatus status, String message,
                                                         HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    private ModelAndView errorView(int status, String title, String message) {
        ModelAndView mv = new ModelAndView("error");
        mv.setStatus(HttpStatus.valueOf(status));
        mv.addObject("status", status);
        mv.addObject("errorTitle", title);
        mv.addObject("errorMessage", message);
        // Страховка: если для @ExceptionHandler-результата @ModelAttribute-методы
        // advice не выполняются, currentUser всё равно попадёт в модель,
        // и сайдбар на странице ошибки отрисуется с данными пользователя.
        User currentUser = resolveCurrentUser(SecurityContextHolder.getContext().getAuthentication());
        if (currentUser != null) {
            mv.addObject("currentUser", currentUser);
        }
        // Роль добавляем всегда: пустая строка безопасна, а null сломал бы
        // сравнение ${role != ''} в сайдбаре.
        mv.addObject("currentRole", roleName(currentUser));
        return mv;
    }
}
