package com.staffsense.demo.service.admin;

import com.staffsense.demo.api.admin.AdminUserDtos;
import com.staffsense.demo.exception.AdminOperationException;
import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Администрирование учётных записей: список, создание, смена роли, блокировка.
 *
 * <p>Почему отдельный сервис, а не логика в контроллере: правила «нельзя
 * заблокировать себя» и «нельзя разжаловать последнего администратора» — это
 * инварианты системы, а не детали HTTP. Контроллер только проверяет право
 * (ADMIN) и переводит доменные исключения в коды ответа.
 *
 * <p>Что сервис <b>не</b> делает: не дублирует каталог сотрудников. Видимость
 * каталога (только активные EMPLOYEE, своя команда для PM) остаётся в
 * {@code EmployeeService} и {@code EmployeeApiController}; здесь — учётные
 * записи целиком, включая заблокированные, потому что без них нельзя
 * разблокировать человека.
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    /** BCrypt учитывает только первые 72 байта пароля — длиннее задавать бессмысленно. */
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;

    /**
     * Заглушка оклада для учётной записи, созданной из администрирования.
     *
     * <p>Оклад — обязательное поле модели (NOT NULL, > 0), но в контракт
     * создания он не входит: администрирование заводит доступ, а не трудовой
     * договор. Поэтому сервер ставит минимально допустимое значение, а реальный
     * оклад заполняется в кадровом процессе. Явно видимая заглушка лучше
     * выдуманной «средней зарплаты».
     */
    private static final BigDecimal PLACEHOLDER_SALARY = new BigDecimal("0.01");

    /** Тот же грейд-заглушка: грейд не влияет на доступ, его уточняет HR. */
    private static final GradeLevel PLACEHOLDER_GRADE = GradeLevel.MIDDLE;

    private static final int DEFAULT_WEEKLY_CAPACITY_HOURS = 40;

    /** Без похожих друг на друга символов: пароль диктуют голосом и переписывают. */
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private static final int GENERATED_PASSWORD_LENGTH = 14;

    private final AdminUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public AdminUserService(AdminUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ── Чтение ───────────────────────────────────────────────────────────

    /**
     * Страница пользователей с фильтром по роли и поиском по ФИО/email/отделу.
     *
     * <p>Заблокированные входят в выдачу: иначе разблокировать их из интерфейса
     * невозможно. Парольный хеш не покидает сервер — в DTO его нет.
     */
    @Transactional(readOnly = true)
    public AdminUserDtos.Page<AdminUserDtos.Item> list(String role, String search, int page, int size) {
        Role filter = parseRoleOrNull(role);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0), safeSize, Sort.by(Sort.Direction.ASC, "fullName"));

        String pattern = "%" + (search == null ? "" : search.trim().toLowerCase(Locale.ROOT)) + "%";
        Page<User> result = filter == null
                ? userRepository.searchAll(pattern, pageable)
                : userRepository.searchByRole(filter, pattern, pageable);

        List<AdminUserDtos.Item> items = result.getContent().stream()
                .map(this::toItem)
                .toList();

        return new AdminUserDtos.Page<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    // ── Создание ─────────────────────────────────────────────────────────

    /**
     * Создать учётную запись.
     *
     * <p>Если пароль не передан — генерируется временный и возвращается один
     * раз в ответе. Пароль хранится только хешем, поэтому «показать пароль
     * позже» технически невозможно: при потере его сбрасывают, а не находят.
     */
    @Transactional
    public AdminUserDtos.Created create(AdminUserDtos.CreateRequest request) {
        Role role = requireRole(request.role());

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new AdminOperationException(
                    "Пользователь с email " + email + " уже существует");
        }

        String generatedPassword = null;
        String rawPassword = request.password();
        if (rawPassword == null || rawPassword.isBlank()) {
            generatedPassword = generatePassword();
            rawPassword = generatedPassword;
        } else {
            validatePassword(rawPassword);
        }

        User user = User.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .department(request.department().trim())
                .position(request.position().trim())
                .gradeLevel(resolveGrade(request.gradeLevel()))
                .salary(request.salary() != null ? request.salary() : PLACEHOLDER_SALARY)
                .weeklyCapacityHours(DEFAULT_WEEKLY_CAPACITY_HOURS)
                .hireDate(LocalDate.now())
                .active(true)
                .build();

        User saved = userRepository.save(user);
        log.info("Создана учётная запись {} (роль {})", saved.getId(), saved.getRole());

        return new AdminUserDtos.Created(toItem(saved), generatedPassword);
    }

    // ── Смена роли ───────────────────────────────────────────────────────

    /**
     * Сменить роль пользователя.
     *
     * <p>Правило «последний администратор»: снять ADMIN с активной учётной
     * записи можно, только если активных администраторов больше одного. Иначе
     * система осталась бы без единственного, кто может управлять доступом.
     *
     * <p>Смена роли самой себе разрешена (если админов больше одного), но
     * вступает в силу при следующем входе: полномочия текущей сессии
     * зафиксированы в {@code SecurityContext} на момент логина.
     */
    @Transactional
    public AdminUserDtos.Item changeRole(Long id, String role, User actor) {
        User target = requireUser(id);
        Role newRole = requireRole(role);

        if (target.getRole() == newRole) {
            return toItem(target);
        }

        if (target.getRole() == Role.ADMIN && target.isActive()) {
            requireNotLastActiveAdmin(
                    "Нельзя снять роль администратора с последнего активного администратора");
        }

        target.setRole(newRole);
        User saved = userRepository.save(target);
        log.info("Роль пользователя {} изменена на {} (инициатор {})",
                saved.getId(), saved.getRole(), actor != null ? actor.getId() : null);
        return toItem(saved);
    }

    // ── Блокировка ───────────────────────────────────────────────────────

    /**
     * Заблокировать или разблокировать учётную запись.
     *
     * <p>Себя заблокировать нельзя: администратор выключил бы себе вход и
     * не смог бы вернуть доступ. Разблокировать себя, разумеется, можно —
     * это проверка только для действия {@code active = false}.
     */
    @Transactional
    public AdminUserDtos.Item setActive(Long id, boolean active, User actor) {
        User target = requireUser(id);

        if (!active && actor != null && actor.getId().equals(target.getId())) {
            throw new AdminOperationException("Нельзя заблокировать самого себя");
        }

        if (target.isActive() == active) {
            return toItem(target);
        }

        target.setActive(active);
        User saved = userRepository.save(target);
        log.info("Учётная запись {} {} (инициатор {})",
                saved.getId(), active ? "разблокирована" : "заблокирована",
                actor != null ? actor.getId() : null);
        return toItem(saved);
    }

    // ── Внутренние методы ────────────────────────────────────────────────

    private User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Пользователь не найден: " + id));
    }

    /**
     * Активных администраторов должно остаться хотя бы двое, иначе менять
     * нечего: единственного админа нельзя ни разжаловать, ни (через смену
     * роли) оставить систему без управления.
     */
    private void requireNotLastActiveAdmin(String message) {
        if (userRepository.countByRoleAndActive(Role.ADMIN, true) <= 1) {
            throw new AdminOperationException(message);
        }
    }

    /** Роль из строки: пустая — «все роли» при фильтрации, неизвестная — 400. */
    private Role parseRoleOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Неизвестная роль: " + raw);
        }
    }

    /** Роль из тела запроса: здесь пустое значение — ошибка, а не «все роли». */
    private Role requireRole(String raw) {
        Role role = parseRoleOrNull(raw);
        if (role == null) {
            throw new IllegalArgumentException("Роль обязательна");
        }
        return role;
    }

    private void validatePassword(String rawPassword) {
        int length = rawPassword.length();
        if (length < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Пароль должен быть не короче " + MIN_PASSWORD_LENGTH + " символов");
        }
        if (length > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Пароль должен быть не длиннее " + MAX_PASSWORD_LENGTH + " символов");
        }
    }

    /**
     * Сброс пароля: генерирует новый временный пароль и сохраняет только хеш.
     *
     * <p>Возвращает пароль один раз — дальше его знает только сотрудник.
     * Почтовой рассылки в системе нет, поэтому пароль передаёт администратор
     * тем же способом, что и при создании учётной записи.
     *
     * @return новый пароль в открытом виде (единственный раз)
     */
    @Transactional
    public String resetPassword(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден: " + userId));

        String raw = generatePassword();
        user.setPasswordHash(passwordEncoder.encode(raw));
        userRepository.save(user);
        log.info("Пароль сброшен администратором для userId={}", userId);
        return raw;
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }

    private AdminUserDtos.Item toItem(User user) {
        return new AdminUserDtos.Item(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                user.getDepartment(),
                user.getPosition(),
                user.isActive());
    }

    /** Грейд из запроса; неизвестное значение — заглушка, чтобы не падать на опечатке. */
    private GradeLevel resolveGrade(String raw) {
        if (raw == null || raw.isBlank()) {
            return PLACEHOLDER_GRADE;
        }
        try {
            return GradeLevel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PLACEHOLDER_GRADE;
        }
    }
}
