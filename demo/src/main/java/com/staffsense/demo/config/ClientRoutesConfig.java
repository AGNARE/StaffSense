package com.staffsense.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Адреса клиентских приложений без «index.html» в строке.
 *
 * <p>Статика отдаётся по точному пути, поэтому {@code /app-next/} сам по себе
 * даёт 404: браузеру нужен файл. Пользователю (и ссылкам в документации) нужен
 * короткий адрес — редиректим на настоящий файл.
 */
@Configuration
public class ClientRoutesConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/app-next", "/app-next/index.html");
        registry.addRedirectViewController("/app-next/", "/app-next/index.html");
        registry.addRedirectViewController("/app", "/app/index.html");
        registry.addRedirectViewController("/app/", "/app/index.html");
    }
}
