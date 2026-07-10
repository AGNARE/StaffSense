package com.staffsense.demo.config;

import com.staffsense.demo.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalControllerAdvice {

    private static final Logger log = LoggerFactory.getLogger(GlobalControllerAdvice.class);

    @ModelAttribute("requestURI")
    public String getRequestURI(HttpServletRequest request) {
        return request.getRequestURI();
    }

    // ── 404 — Resource not found ──────────────────────────────────────
    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
    public ModelAndView handleNotFound(Exception ex) {
        return errorView(HttpStatus.NOT_FOUND.value(), "Страница не найдена",
                "Запрашиваемый ресурс не существует или был удалён.");
    }

    // ── 403 — Access denied ───────────────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView handleAccessDenied() {
        return errorView(HttpStatus.FORBIDDEN.value(), "Доступ запрещён",
                "У вас нет прав для просмотра этой страницы.");
    }

    // ── 400 — Bad request (IllegalArgument, validation) ──────────────
    @ExceptionHandler(IllegalArgumentException.class)
    public ModelAndView handleBadRequest(IllegalArgumentException ex) {
        return errorView(HttpStatus.BAD_REQUEST.value(), "Неверный запрос",
                ex.getMessage() != null ? ex.getMessage() : "Некорректные данные запроса.");
    }

    // ── 500 — Unexpected errors ──────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ModelAndView handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for URI {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return errorView(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Внутренняя ошибка сервера",
                "Произошла непредвиденная ошибка. Пожалуйста, попробуйте позже.");
    }

    private ModelAndView errorView(int status, String title, String message) {
        ModelAndView mv = new ModelAndView("error");
        mv.setStatus(HttpStatus.valueOf(status));
        mv.addObject("status", status);
        mv.addObject("errorTitle", title);
        mv.addObject("errorMessage", message);
        return mv;
    }
}
