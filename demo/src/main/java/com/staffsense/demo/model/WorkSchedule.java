package com.staffsense.demo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Запись табеля рабочего времени за один день (модуль C).
 *
 * <p>Часы вносит руководитель по своей команде либо HR/ADMIN (BR-027).
 * Сотрудник свои часы не редактирует — только просматривает.
 */
@Entity
@Table(name = "work_schedule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** Фактически отработанные часы. */
    @Column(name = "hours_worked")
    private Double hoursWorked;

    @Column(name = "is_remote", nullable = false)
    @Builder.Default
    private boolean remote = false;

    @Column(name = "is_sick_leave", nullable = false)
    @Builder.Default
    private boolean sickLeave = false;

    /** День отпуска: проставляется автоматически из утверждённой служебной записки. */
    @Column(name = "is_vacation", nullable = false)
    @Builder.Default
    private boolean vacation = false;

    @Column(name = "overtime_hours")
    @Builder.Default
    private Double overtimeHours = 0.0;

    /** Кто внёс запись: руководитель или HR/ADMIN. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        this.updatedAt = LocalDateTime.now();
        if (this.overtimeHours == null) {
            this.overtimeHours = 0.0;
        }
    }
}
