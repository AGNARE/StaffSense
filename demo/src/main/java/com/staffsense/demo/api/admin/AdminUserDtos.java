package com.staffsense.demo.api.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Контракт администрирования пользователей и матрицы доступа.
 *
 * <p>Главное обещание контракта — минимум персональных данных. Наружу уходят
 * только те поля, которые нужны таблице и форме: идентификатор, ФИО, служебный
 * email, роль, отдел, должность и признак активности. Парольный хеш, оклад,
 * грейд, руководитель и кадровые атрибуты остаются на сервере: экран
 * администрирования не должен становиться вторым каталогом ПДн.
 *
 * <p>Пароль при создании можно передать в запросе ({@code password}), но обычно
 * его не передают: сервер генерирует временный пароль и возвращает его
 * <b>однократно</b> в ответе на создание ({@link Created#temporaryPassword()}).
 * В базе хранится только BCrypt-хеш, в журналы и повторные ответы пароль
 * не попадает.
 */
public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    /** Строка таблицы пользователей: без пароля и без лишних ПДн. */
    public record Item(
            Long userId,
            String fullName,
            String email,
            String role,
            String department,
            String position,
            boolean active
    ) {
    }

    /** Страница списка — та же форма, что у каталога сотрудников. */
    public record Page<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    /**
     * Создание учётной записи.
     *
     * <p>Обязательные поля — ФИО, email, роль, отдел, должность. Пароль
     * необязателен: пустой или отсутствующий означает «сгенерируй временный».
     * Оклад, грейд и дата найма в контракт не входят — сервер подставляет
     * заглушки, которые заполняются в кадровом процессе (см. AdminUserService).
     */
    public record CreateRequest(
            @NotBlank(message = "ФИО обязательно")
            @Size(max = 150, message = "ФИО не длиннее 150 символов")
            String fullName,

            @NotBlank(message = "Email обязателен")
            @Email(message = "Некорректный email")
            @Size(max = 150, message = "Email не длиннее 150 символов")
            String email,

            @NotBlank(message = "Роль обязательна")
            String role,

            @NotBlank(message = "Отдел обязателен")
            @Size(max = 100, message = "Отдел не длиннее 100 символов")
            String department,

            @NotBlank(message = "Должность обязательна")
            @Size(max = 100, message = "Должность не длиннее 100 символов")
            String position,

    /** Необязателен; при пустом значении генерируется на сервере. */
    @Size(max = 72, message = "Пароль не длиннее 72 символов (ограничение BCrypt)")
    String password,

    /** Оклад, сом. Необязателен, но без него стоимость под риском не считается. */
    @DecimalMin(value = "1", message = "Оклад должен быть положительным")
    BigDecimal salary,

    /** Грейд: JUNIOR / MIDDLE / SENIOR / LEAD. Необязателен. */
    String gradeLevel
    ) {
    }

    /**
     * Результат создания.
     *
     * @param temporaryPassword сгенерированный пароль; {@code null}, если пароль
     *                          передал клиент. Показывается один раз.
     */
    public record Created(
            Item user,
            String temporaryPassword
    ) {
    }

    /** Смена роли. */
    public record RoleRequest(
            @NotBlank(message = "Роль обязательна")
            String role
    ) {
    }

    /** Блокировка или разблокировка. */
    public record ActiveRequest(
            @NotNull(message = "Признак активности обязателен")
            Boolean active
    ) {
    }

    /**
     * Матрица доступа «роль × раздел».
     *
     * <p>Список разделов не дублируется: он собран из
     * {@code RoleScreens.forRole(...)} — того же источника, по которому
     * строится навигация в {@code /api/v1/me}. Клиент рисует только то,
     * что прислал сервер.
     *
     * @param screens объединение разделов всех ролей в порядке появления
     * @param roles   роли и идентификаторы доступных им разделов
     */
    public record Access(
            List<ScreenDto> screens,
            List<RoleAccess> roles
    ) {
    }

    /** Раздел навигации. */
    public record ScreenDto(
            String id,
            String title,
            String route,
            String group
    ) {
    }

    /** Что доступно роли: идентификаторы разделов. */
    public record RoleAccess(
            String role,
            String title,
            List<String> screens
    ) {
    }
}
