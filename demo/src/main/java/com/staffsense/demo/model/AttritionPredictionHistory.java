package com.staffsense.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Снимок прогноза увольнения на момент расчёта (append-only журнал).
 *
 * <p>Дополняет {@link AttritionPrediction}, который по BR-010 хранит только
 * один актуальный прогноз и удаляет предыдущие. Здесь ничего не удаляется:
 * каждая строка — это «что модель думала в такой-то момент».
 *
 * <p>Зачем нужно:
 * <ul>
 *   <li>тренд риска по сотруднику (сейчас построить нечем);</li>
 *   <li>выгрузка «за неделю / месяц / квартал» — по {@code computedAt};</li>
 *   <li>проверка, сбывались ли прогнозы: снимок на дату + последующий факт.</li>
 * </ul>
 *
 * <p>Поле {@code source} отделяет расчёт ML-сервисом от эвристики-фолбэка:
 * смешивать их в отчётах нельзя, у них разная природа.
 */
@Entity
@Table(name = "attrition_prediction_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttritionPredictionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    /** ML — расчёт сервисом, HEURISTIC — фолбэк, SEED — демо-данные. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private PredictionSource source;

    /** Кто инициировал расчёт; null для фоновых и демо-записей. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "computed_by_id")
    private User computedBy;

    @Column(name = "features_snapshot_json", columnDefinition = "TEXT")
    private String featuresSnapshotJson;

    @Column(name = "top_risk_factors_json", columnDefinition = "TEXT")
    private String topRiskFactorsJson;

    @PrePersist
    protected void onCreate() {
        if (computedAt == null) {
            computedAt = LocalDateTime.now();
        }
    }
}
