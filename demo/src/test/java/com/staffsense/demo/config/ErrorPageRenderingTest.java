package com.staffsense.demo.config;

import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Регрессия: страница ошибки должна рендериться при {@code currentUser == null}.
 *
 * <p>Раньше {@code fragments/layout} обращался к {@code currentUser.role.name()}
 * без проверки на null. Страницы ошибки рендерятся вне обычных контроллеров
 * ({@code BasicErrorController} для {@code /error} и {@code @ExceptionHandler}
 * в {@link GlobalControllerAdvice}), поэтому {@code currentUser} в модели
 * отсутствовал, и вместо 404/403 пользователь получал 500 с
 * {@code SpelEvaluationException: Property or field 'role' cannot be found on null}.
 *
 * <p>Фильтры безопасности отключены намеренно ({@code addFilters = false}):
 * тест проверяет рендер шаблона, а не правила доступа. По той же причине
 * проверяются только ASCII-маркеры разметки — без CharacterEncodingFilter
 * кодировка ответа в MockMvc не гарантирована.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ErrorPageRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Страница ошибки рендерится без currentUser и не падает в 500")
    void errorPageRendersWithoutCurrentUser() throws Exception {
        // Accept: text/html обязателен — BasicErrorController без него отдаёт JSON.
        mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("error-code")))
                // currentUser == null: сайдбар показывает гостя со ссылкой на вход
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(not(containsString("/logout"))));
    }

    @Test
    @DisplayName("Контейнерный /error с авторизованным пользователем получает currentUser из advice")
    void basicErrorControllerGetsCurrentUserFromAdvice() throws Exception {
        User admin = userRepository.findByEmail("admin@staffsense.com").orElseThrow();
        var authentication = new UsernamePasswordAuthenticationToken(admin.getEmail(), "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Именно этот путь давал шторм ошибок: 403 от фильтров безопасности и 404
        // на статику обрабатывает контейнер через BasicErrorController, без контроллера.
        mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                        .principal(authentication)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("error-code")))
                .andExpect(content().string(containsString("/logout")));
    }

    @Test
    @DisplayName("Ошибка из контроллера рендерится с currentUser: сайдбар с кнопкой выхода")
    void adviceErrorViewIncludesCurrentUser() throws Exception {
        User admin = userRepository.findByEmail("admin@staffsense.com").orElseThrow();
        var authentication = new UsernamePasswordAuthenticationToken(admin.getEmail(), "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        // SecurityContext нужен @PreAuthorize, principal в запросе — резолверу
        // аргумента Authentication (фильтры безопасности в этом тесте отключены).
        SecurityContextHolder.getContext().setAuthentication(authentication);

        mockMvc.perform(get("/employees/999999").principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("error-code")))
                // currentUser попал в модель: рендерится форма выхода, а не «Гость»
                .andExpect(content().string(containsString("/logout")));
    }
}
