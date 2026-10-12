package com.staffsense.demo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Закрытие периода табеля (месяца) для команды руководителя.
 *
 * <p>После закрытия правка записей за этот период запрещена (BR-028),
 * иначе отчёт перестанет соответствовать данным.
 */
@Entity
@Table(name = "work_schedule_period")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkSchedulePeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Чей участок закрывается — команда этого руководителя. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    @NotNull
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @NotNull
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TimesheetPeriodStatus status = TimesheetPeriodStatus.OPEN;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_id")
    private User closedBy;

    /** Человекочитаемая подпись периода: «Октябрь 2026». */
    @Transient
    public String getPeriodLabel() {
        if (periodStart == null) {
            return "—";
        }
        String[] months = {"январь", "февраль", "март", "апрель", "май", "июнь",
                "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"};
        return months[periodStart.getMonthValue() - 1] + " " + periodStart.getYear();
    }
}
