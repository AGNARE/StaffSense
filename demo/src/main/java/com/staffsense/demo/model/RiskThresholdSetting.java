package com.staffsense.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Настраиваемые пороги уровней риска (FR-039, BR-007).
 *
 * <p>Одна строка на всю систему: пороги — глобальная настройка, а не набор
 * записей. Если строки нет, действуют значения из {@code application.yml}
 * ({@code risk.thresholds.low/medium}).
 *
 * <p>Правило BR-007: {@code 0 < low < medium < 100}.
 */
@Entity
@Table(name = "risk_threshold_setting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RiskThresholdSetting {

    /** Единственная допустимая строка. */
    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    /** Граница LOW/MEDIUM: оценки ниже — LOW. */
    @Column(name = "low_bound", nullable = false)
    private Double lowBound;

    /** Граница MEDIUM/HIGH: оценки выше или равные — HIGH. */
    @Column(name = "medium_bound", nullable = false)
    private Double mediumBound;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Кто изменил пороги; null — изменение при первом запуске/миграции. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private User updatedBy;
}
