package com.staffsense.demo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "workload_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkloadRecord {

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

    @NotNull
    @Column(name = "total_estimated_hours", nullable = false)
    private Double totalEstimatedHours;

    @NotNull
    @Column(name = "capacity_hours", nullable = false)
    private Double capacityHours;

    @NotNull
    @Column(name = "workload_index", nullable = false)
    private Double workloadIndex;

    @Column(name = "overtime_hours")
    private Double overtimeHours;

    @Column(name = "computed_at", nullable = false)
    @Builder.Default
    private LocalDateTime computedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (this.computedAt == null) {
            this.computedAt = LocalDateTime.now();
        }
    }
}
