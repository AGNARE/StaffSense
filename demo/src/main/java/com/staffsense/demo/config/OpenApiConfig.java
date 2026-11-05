package com.staffsense.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Описание REST-контракта для Swagger UI.
 *
 * <p>Зачем: клиент (сейчас это наш собственный фронтенд на /app, дальше — любой)
 * не должен узнавать формат ответов чтением Java-кода. После добавления
 * springdoc контракт генерируется из самих контроллеров и DTO, поэтому
 * расхождение между кодом и документацией исключено по построению.
 *
 * <p>Доступ: страницы документации закрыты той же авторизацией, что и API
 * (SecurityConfig не делает для них исключения) — схема API не публикуется
 * наружу.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI staffSenseOpenApi() {
        return new OpenAPI().info(new Info()
                .title("StaffSense API")
                .version("v1")
                .description("""
                        Контракт StaffSense: риски увольнений, каталог сотрудников,
                        пороги уровней риска.

                        Все эндпоинты требуют аутентификации (сессионная cookie).
                        Роли: ADMIN, HR, EXEC, PM. Руководитель проекта (PM) видит
                        только сотрудников своей команды — ограничение действует
                        и в API, поэтому часть записей в ответах будет отсутствовать.
                        """)
                .contact(new Contact().name("StaffSense"))
                .license(new License().name("MIT")));
    }
}
