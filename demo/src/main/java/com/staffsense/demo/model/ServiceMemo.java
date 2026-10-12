package com.staffsense.demo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Служебная записка (модуль C).
 *
 * <p>Подать записку может любой сотрудник (BR-029). Маршрут согласования:
 * {@code PENDING_PM → PENDING_HR → APPROVED} с возможностью отклонения
 * на любом этапе и отзыва автором (BR-003 ТЗ).
 */
@Entity
@Table(name = "service_memo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceMemo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Автор записки. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "memo_type", nullable = false, length = 30)
    private MemoType memoType;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MemoStatus status = MemoStatus.PENDING_PM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pm_decision_by_id")
    private User pmDecisionBy;

    @Column(name = "pm_decision_at")
    private LocalDateTime pmDecisionAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hr_decision_by_id")
    private User hrDecisionBy;

    @Column(name = "hr_decision_at")
    private LocalDateTime hrDecisionAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    /** Число календарных дней периода (включительно). */
    @Transient
    public long getDaysCount() {
        if (startDate == null || endDate == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    /** Можно ли ещё править или отозвать записку. */
    @Transient
    public boolean isEditable() {
        return !status.isFinal();
    }

    /** Учитывается ли запись в табеле (только утверждённые). */
    @Transient
    public boolean isAffectsTimesheet() {
        return status == MemoStatus.APPROVED
                && (memoType == MemoType.SICK_LEAVE || memoType == MemoType.VACATION);
    }
}
