package com.staffsense.demo.dto.hr;

import com.staffsense.demo.model.Gender;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Форма приёма сотрудника (модуль «Тундук»).
 *
 * <p>Содержит три группы полей:
 * <ol>
 *   <li>шаг верификации — ИНН и согласие на обработку персональных данных;</li>
 *   <li>персональные данные — заполняются из «Тундук», при ERR-014 вводятся вручную;</li>
 *   <li>корпоративные данные — всегда заполняет HR.</li>
 * </ol>
 *
 * <p>Ограничения {@code @NotBlank}/{@code @NotNull} относятся к корпоративной
 * группе: они проверяются только на шаге сохранения (второй шаг формы),
 * чтобы шаг верификации мог пройти с пустыми корпоративными полями.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HireEmployeeForm {

    // ── Шаг 1: верификация ───────────────────────────────────────────────
    private String inn;

    /** BR-024: согласие кандидата на обработку персональных данных. */
    private boolean consentGiven;

    // ── Шаг 2: персональные данные (из «Тундук») ─────────────────────────
    @NotBlank(message = "ФИО обязательно")
    @Size(max = 150)
    private String fullName;

    private LocalDate birthDate;

    private Gender gender;

    @Size(max = 20)
    private String pin;

    @Size(max = 4)
    private String passportSeries;

    @Size(max = 6)
    private String passportNumber;

    @Size(max = 255)
    private String passportIssuedBy;

    private LocalDate passportIssueDate;

    @Size(max = 500)
    private String registrationAddress;

    @Size(max = 500)
    private String photoUrl;

    /** true, если данные подтверждены госсистемой. */
    private boolean tundukVerified;

    // ── Шаг 2: корпоративные данные (всегда заполняет HR) ────────────────
    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный email")
    @Size(max = 150)
    private String email;

    @NotNull(message = "Роль обязательна")
    private Role role;

    @NotBlank(message = "Отдел обязателен")
    @Size(max = 100)
    private String department;

    @NotBlank(message = "Должность обязательна")
    @Size(max = 100)
    private String position;

    @NotNull(message = "Грейд обязателен")
    private GradeLevel gradeLevel;

    @NotNull(message = "Оклад обязателен")
    @DecimalMin(value = "0.01", message = "Оклад должен быть больше нуля")
    private BigDecimal salary;

    @Min(value = 1, message = "Ёмкость должна быть не меньше 1 часа")
    private Integer weeklyCapacityHours;

    @NotNull(message = "Дата найма обязательна")
    private LocalDate hireDate;

    private Long managerId;

    /** Пароль для новой учётной записи; при пустом значении будет сгенерирован. */
    @Size(max = 100)
    private String password;
}
