package com.staffsense.demo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "attrition_predictions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttritionPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    @NotNull
    @Min(0)
    @Max(100)
    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "ai_action_plan", columnDefinition = "TEXT")
    private String aiActionPlan;

    @Column(name = "features_snapshot_json", columnDefinition = "TEXT")
    private String featuresSnapshotJson;

    /**
     * Факторы риска (XAI) в формате JSON.
     *
     * <p>ML-сервис возвращал {@code topRiskFactors}, но платформа их нигде
     * не сохраняла и не показывала — объяснение прогноза терялось.
     */
    @Column(name = "top_risk_factors_json", columnDefinition = "TEXT")
    private String topRiskFactorsJson;

    @PrePersist
    protected void onCreate() {
        this.computedAt = LocalDateTime.now();
    }
}
