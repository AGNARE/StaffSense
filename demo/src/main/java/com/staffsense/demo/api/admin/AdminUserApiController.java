package com.staffsense.demo.api.admin;

import com.staffsense.demo.api.me.RoleScreens;
import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.admin.AdminUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST-контракт администрирования пользователей и матрицы доступа.
 *
 * <p>Заменяет две серверные страницы ({@code /admin} — список пользователей,
 * {@code /admin/users/new} — создание): новый клиент получает данные, а не
 * HTML, и меняет роль или блокировку без перезагрузки.
 *
 * <p>Права — только ADMIN, причём в двух местах: {@code @PreAuthorize} на
 * классе (работает через {@code @EnableMethodSecurity}) и правило
 * {@code /admin/**} в {@code SecurityConfig} для старых страниц. Если одно
 * из них снимут при рефакторинге, второе останется.
 *
 * <p>Матрица доступа не дублирует список разделов: она собирается из
 * {@link RoleScreens#forRole(Role)} — того же источника, что отдаёт
 * {@code /api/v1/me}. Поэтому меню в шапке и таблица прав не могут разойтись.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Администрирование пользователей",
        description = "Учётные записи, роли и матрица доступа")
public class AdminUserApiController {

    /**
     * Русские названия ролей для матрицы.
     *
     * <p>Заголовки экранов уже живут в {@code RoleScreens}; название самой роли
     * — подпись колонки, а не правило доступа, поэтому хранится здесь.
     */
    private static final Map<Role, String> ROLE_TITLES = Map.of(
            Role.ADMIN, "Администратор",
            Role.EXEC, "Руководство компании",
            Role.HR, "HR-специалист",
            Role.PM, "Руководитель проекта",
            Role.EMPLOYEE, "Сотрудник");

    private final AdminUserService adminUserService;
    private final UserRepository userRepository;

    public AdminUserApiController(AdminUserService adminUserService,
                                  UserRepository userRepository) {
        this.adminUserService = adminUserService;
        this.userRepository = userRepository;
    }

    /**
     * Список пользователей.
     *
     * @param role   необязательный фильтр: ADMIN, EXEC, HR, PM, EMPLOYEE
     * @param search необязательный поиск по ФИО, email и отделу
     */
    @GetMapping("/users")
    public AdminUserDtos.Page<AdminUserDtos.Item> users(
            @RequestParam(name = "role", defaultValue = "") String role,
            @RequestParam(name = "search", defaultValue = "") String search,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        return adminUserService.list(role, search, page, size);
    }

    /**
     * Создание учётной записи.
     *
     * <p>Ответ 201 и, если пароль не передали, одноразовый
     * {@code temporaryPassword} — показать его можно ровно один раз.
     */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserDtos.Created create(@Valid @RequestBody AdminUserDtos.CreateRequest request) {
        return adminUserService.create(request);
    }

    /** Смена роли. Ответ — обновлённая строка таблицы. */
    @PostMapping("/users/{id}/role")
    public AdminUserDtos.Item changeRole(@PathVariable Long id,
                                         @Valid @RequestBody AdminUserDtos.RoleRequest request,
                                         Authentication auth) {
        return adminUserService.changeRole(id, request.role(), currentUser(auth));
    }

    /** Блокировка ({@code active=false}) и разблокировка ({@code active=true}). */
    @PostMapping("/users/{id}/active")
    public AdminUserDtos.Item setActive(@PathVariable Long id,
                                        @Valid @RequestBody AdminUserDtos.ActiveRequest request,
                                        Authentication auth) {
        return adminUserService.setActive(id, Boolean.TRUE.equals(request.active()), currentUser(auth));
    }

    /** Матрица доступа «роль × раздел». */
    @GetMapping("/access")
    public AdminUserDtos.Access access() {
        Map<String, AdminUserDtos.ScreenDto> screens = new LinkedHashMap<>();
        List<AdminUserDtos.RoleAccess> roles = new ArrayList<>();

        // Порядок ролей — порядок объявления в enum: администратор первым
        // (самая широкая матрица), сотрудник последним (самый короткий список).
        // Клиент рисует колонки как есть, не сортируя.
        for (Role role : Role.values()) {
            List<RoleScreens.Screen> roleScreens = RoleScreens.forRole(role);
            roles.add(new AdminUserDtos.RoleAccess(
                    role.name(),
                    ROLE_TITLES.getOrDefault(role, role.name()),
                    roleScreens.stream().map(RoleScreens.Screen::id).toList()));
            for (RoleScreens.Screen screen : roleScreens) {
                // putIfAbsent: раздел «мой кабинет» есть у всех, а порядок
                // колонок задаёт первая роль, у которой он появился.
                screens.putIfAbsent(screen.id(), new AdminUserDtos.ScreenDto(
                        screen.id(), screen.title(), screen.route(), screen.group()));
            }
        }

        return new AdminUserDtos.Access(List.copyOf(screens.values()), roles);
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private User currentUser(Authentication auth) {
        if (auth == null) {
            throw new ResourceNotFoundException("Пользователь не определён");
        }
        return userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new ResourceNotFoundException("Пользователь не найден: " + auth.getName()));
    }
}
