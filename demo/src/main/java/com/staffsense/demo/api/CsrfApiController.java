package com.staffsense.demo.api;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Отдаёт клиенту имя заголовка и значение CSRF-токена.
 *
 * <p>Зачем отдельная ручка: SPA делает POST-запросы (смена порогов, пересчёт),
 * а Spring Security 6 маскирует значение токена в cookie, поэтому подставлять
 * cookie напрямую нельзя — клиент спрашивает токен у сервера и присылает его
 * в заголовке. Это дешевле и надёжнее, чем разбирать мета-теги из HTML.
 */
@RestController
@RequestMapping("/api/v1/csrf")
public class CsrfApiController {

    @GetMapping
    public CsrfDto csrf(CsrfToken token) {
        return new CsrfDto(token.getHeaderName(), token.getToken());
    }

    public record CsrfDto(String headerName, String token) {
    }
}
