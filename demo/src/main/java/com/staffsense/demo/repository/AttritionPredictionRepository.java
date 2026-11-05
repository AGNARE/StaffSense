package com.staffsense.demo.repository;

import com.staffsense.demo.model.AttritionPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttritionPredictionRepository extends JpaRepository<AttritionPrediction, Long> {

    List<AttritionPrediction> findByUserId(Long userId);

    /**
     * Последний прогноз сотрудника.
     *
     * <p>Добавлено, чтобы убрать N+1: ранее каждый вызов в цикле по сотрудникам
     * делал {@code findAll()} и искал максимум по computedAt в памяти
     * (RiskController, EmployeeController, CsvExportService, ModelInsightService).
     */
    Optional<AttritionPrediction> findFirstByUserIdOrderByComputedAtDesc(Long userId);

    List<AttritionPrediction> findByUserIdIn(List<Long> userIds);

    /**
     * Массовый пересчёт уровня по баллу — после смены порогов (FR-039).
     *
     * <p>Диапазон задаётся как {@code [from, to)}: границы соседних уровней не
     * пересекаются. Возвращает число обновлённых строк.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
            UPDATE AttritionPrediction p SET p.riskLevel = :level
            WHERE p.riskScore >= :from AND p.riskScore < :to
            """)
    int updateLevelInRange(@org.springframework.data.repository.query.Param("level") com.staffsense.demo.model.RiskLevel level,
                           @org.springframework.data.repository.query.Param("from") double from,
                           @org.springframework.data.repository.query.Param("to") double to);
}
