package com.staffsense.demo.config;

import com.staffsense.demo.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Конфигурация безопасности.
 *
 * <p>Матрица доступа (раздел 21 ТЗ):
 * <ul>
 *   <li>/admin/**         — ADMIN</li>
 *   <li>/risk/**          — ADMIN, EXEC, HR, PM</li>
 *   <li>/analytics/**     — ADMIN, EXEC, HR, PM (ранее — любой аутентифицированный:
 *       EMPLOYEE видел риски, KPI и зарплаты коллег — 🔴 SECURITY GAP)</li>
 *   <li>/surveys/manage/**— ADMIN, EXEC, HR, PM</li>
 *   <li>/employees/**     — ADMIN, HR, PM</li>
 *   <li>остальное         — аутентифицированный пользователь + проверки владельца
 *       в сервисном слое (см. TeamAccessService)</li>
 * </ul>
 *
 * <p>CSRF включён для всех путей, включая /api/**. Ранее стояло
 * {@code ignoringRequestMatchers("/api/**")}, из-за чего POST-мутации задач
 * были уязвимы к CSRF. Единственный AJAX-вызов (канбан-доска) уже передаёт
 * заголовок X-CSRF-TOKEN.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** NFR-005 / КП: BCrypt с 12 раундами. */
    private static final int BCRYPT_STRENGTH = 12;

    private final CustomUserDetailsService userDetailsService;
    private final RateLimitingFilter rateLimitingFilter;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          RateLimitingFilter rateLimitingFilter) {
        this.userDetailsService = userDetailsService;
        this.rateLimitingFilter = rateLimitingFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .userDetailsService(userDetailsService)
            .authorizeHttpRequests(auth -> auth
                // /error — служебная страница ошибок: её рендерит контейнер/фильтры,
                // и она обязана быть доступна без прав, иначе 403/404 превращаются
                // в цикл редиректов вместо внятного сообщения.
                .requestMatchers("/login", "/error", "/css/**", "/js/**", "/images/**", "/img/**", "/favicon.ico").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Модуль «Тундук»: приём сотрудника и личные дела — только HR/ADMIN
                // (обработка паспортных данных и ПИН).
                .requestMatchers("/hr/**").hasAnyRole("ADMIN", "HR")
                // Портал онбординга: управление содержимым — HR/ADMIN,
                // чтение — любой сотрудник. Порядок правил важен.
                .requestMatchers("/onboarding/manage/**").hasAnyRole("ADMIN", "HR")
                .requestMatchers("/onboarding/**").authenticated()
                // Справочник сотрудников открыт всем (ограниченный набор полей).
                .requestMatchers("/directory/**").authenticated()
                // Кадровые процессы: табель и служебные записки — любой сотрудник,
                // детальные права (кто чей табель ведёт, кто визирует) проверяются в сервисах.
                .requestMatchers("/timesheet/**").authenticated()
                .requestMatchers("/memos/**").authenticated()
                .requestMatchers("/risk/**").hasAnyRole("ADMIN", "EXEC", "HR", "PM")
                .requestMatchers("/analytics/**").hasAnyRole("ADMIN", "EXEC", "HR", "PM")
                .requestMatchers("/surveys/manage/**").hasAnyRole("ADMIN", "EXEC", "HR", "PM")
                .requestMatchers("/employees/**").hasAnyRole("ADMIN", "HR", "PM")
                .requestMatchers("/oneonone/**").authenticated()
                .requestMatchers("/surveys/take/**").authenticated()
                .requestMatchers("/dashboard/**").authenticated()
                .requestMatchers("/projects/**").authenticated()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .sessionManagement(session -> session
                .invalidSessionUrl("/login?expired")
            )
            // Для REST-клиентов (/api/**) отдаём 401 вместо редиректа на HTML-форму логина.
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        new AntPathRequestMatcher("/api/**"))
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
