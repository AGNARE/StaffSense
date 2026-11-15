package com.staffsense.demo.api.directory;

import com.staffsense.demo.dto.directory.DirectoryEntryDto;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Справочник сотрудников для React-клиента (FR-043).
 *
 * <p>Доступен любому аутентифицированному сотруднику — и это определяет
 * контракт: 🔴 отдаются только ФИО, должность, отдел, рабочий e-mail и
 * руководитель. Оклад, KPI, нагрузка и прогноз риска в ответ не попадают
 * (BR-031): справочник и раздел «Сотрудники» — разные экраны с разными правами.
 *
 * <p>Домена «справочник» в модели нет, поэтому данные собираются из
 * {@code UserRepository} по активным сотрудникам. Отдельной сущности не
 * заводим: дублировать кадровые данные ради одного экрана — значит получить
 * второй источник правды, который разойдётся с первым.
 *
 * <p>Параметры {@code department} и {@code q} необязательны и повторяют
 * фильтры серверной страницы: клиент может искать на сервере, а не тянуть
 * весь список. Счётчики подразделений при этом всегда считаются по компании
 * целиком — иначе выбранный фильтр «съедал» бы остальные чипы.
 */
@RestController
@RequestMapping("/api/v1/directory")
@Tag(name = "Справочник", description = "Неконфиденциальные контакты и структура компании")
public class DirectoryApiController {

    private final UserRepository userRepository;

    public DirectoryApiController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public DirectoryApiDtos.Response directory(
            @RequestParam(name = "department", required = false) String department,
            @RequestParam(name = "q", required = false) String q) {

        List<User> active = userRepository.findByActive(true);
        String dept = department == null ? "" : department.trim();
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        List<DirectoryEntryDto> employees = active.stream()
                .filter(user -> dept.isBlank() || dept.equals(user.getDepartment()))
                .filter(user -> matches(user, query))
                .sorted(Comparator.comparing(User::getFullName, Comparator.nullsLast(String::compareTo)))
                .map(DirectoryApiController::toEntry)
                .toList();

        return new DirectoryApiDtos.Response(
                active.size(),
                employees.size(),
                departments(active),
                employees,
                managers(active));
    }

    // ── сборка ответа ────────────────────────────────────────────────────

    /** Подразделения со счётчиками: по алфавиту, пустое название — отдельной группой. */
    private static List<DirectoryApiDtos.DepartmentGroup> departments(List<User> active) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (User user : active) {
            counts.merge(departmentOf(user), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new DirectoryApiDtos.DepartmentGroup(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(DirectoryApiDtos.DepartmentGroup::name))
                .toList();
    }

    /**
     * Руководители и число прямых подчинённых.
     *
     * <p>Руководитель попадает в список, только если он сам активен и у него
     * есть хотя бы один активный подчинённый: «руководитель без команды» в
     * справочнике структуры — шум.
     */
    private static List<DirectoryApiDtos.Manager> managers(List<User> active) {
        Map<Long, Long> reports = new LinkedHashMap<>();
        for (User user : active) {
            if (user.getManager() != null && user.getManager().getId() != null) {
                reports.merge(user.getManager().getId(), 1L, Long::sum);
            }
        }

        return active.stream()
                .filter(user -> reports.containsKey(user.getId()))
                .map(user -> new DirectoryApiDtos.Manager(
                        user.getId(),
                        user.getFullName(),
                        user.getPosition(),
                        user.getDepartment(),
                        user.getEmail(),
                        reports.get(user.getId())))
                .sorted(Comparator
                        .comparingLong(DirectoryApiDtos.Manager::directReports).reversed()
                        .thenComparing(DirectoryApiDtos.Manager::fullName,
                                Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    /** Поиск по имени, должности, отделу и почте — как на серверной странице. */
    private static boolean matches(User user, String query) {
        if (query.isEmpty()) {
            return true;
        }
        return contains(user.getFullName(), query)
                || contains(user.getPosition(), query)
                || contains(user.getDepartment(), query)
                || contains(user.getEmail(), query);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String departmentOf(User user) {
        String department = user.getDepartment();
        return department == null || department.isBlank() ? "Без подразделения" : department;
    }

    /** 🔴 Только безопасные поля: оклада и производных показателей здесь нет. */
    private static DirectoryEntryDto toEntry(User user) {
        return new DirectoryEntryDto(
                user.getId(),
                user.getFullName(),
                user.getPosition(),
                user.getDepartment(),
                user.getEmail(),
                user.getManager() != null ? user.getManager().getFullName() : null);
    }
}
