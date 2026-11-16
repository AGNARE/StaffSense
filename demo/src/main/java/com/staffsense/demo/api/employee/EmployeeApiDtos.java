package com.staffsense.demo.api.employee;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO контракта {@code /api/v1/employees}.
 *
 * <p>Второй ресурс в API-first контуре. Задача та же, что у рисков: отдать
 * данные без сущностей JPA и без привязки к конкретному клиенту. Поля профиля
 * ограничены тем, что видно в каталоге и карточке; персональные данные
 * (документы, зарплата) сюда не попадают намеренно.
 */
public final class EmployeeApiDtos {

    private EmployeeApiDtos() {
    }

    /** Строка каталога. */
    public record Item(
            Long userId,
            String fullName,
            String email,
            String department,
            String position,
            String gradeLevel,
            String managerName,
            LocalDate hireDate,
            boolean active
    ) {
    }

    /** Страница каталога. */
    public record Page<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    /** Карточка сотрудника: профиль + последний прогноз риска, если он есть. */
    public record Detail(
            Long userId,
            String fullName,
            String email,
            String department,
            String position,
            String gradeLevel,
            String managerName,
            LocalDate hireDate,
            boolean active,
            Integer weeklyCapacityHours,
            RiskSummary risk
    ) {
    }

    public record RiskSummary(
            Double riskScore,
            String riskLevel,
            LocalDateTime computedAt
    ) {
    }

    /** Справочник отделов для фильтра. */
    public record Departments(List<String> items) {
    }
}
