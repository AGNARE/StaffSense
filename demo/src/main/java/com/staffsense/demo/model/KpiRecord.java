package com.staffsense.demo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "kpi_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpiRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @NotNull
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Min(0)
    @Max(1)
    @Column(name = "on_time_completion_rate")
    private Double onTimeCompletionRate;

    @Min(0)
    @Max(1)
    @Column(name = "reopen_rate")
    private Double reopenRate;

    @DecimalMin("1.0")
    @DecimalMax("5.0")
    @Column(name = "manager_rating")
    private Double managerRating;

    @Min(0)
    @Max(100)
    @Column(name = "kpi_score")
    private Double kpiScore;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    @PrePersist
    protected void onCreate() {
        this.computedAt = LocalDateTime.now();
    }
}
