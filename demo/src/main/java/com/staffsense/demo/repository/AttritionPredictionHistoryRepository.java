package com.staffsense.demo.repository;

import com.staffsense.demo.model.AttritionPredictionHistory;
import com.staffsense.demo.model.PredictionSource;
import com.staffsense.demo.model.RiskLevel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Журнал снимков прогноза (append-only).
 *
 * <p>Используется в трёх сценариях:
 * <ul>
 *   <li>тренд риска по сотруднику — {@link #findByUserIdOrderByComputedAtDesc};</li>
 *   <li>выгрузка за период — {@link #findByComputedAtBetweenOrderByComputedAtDesc};</li>
 *   <li>администрирование — сводка по уровням и последние записи.</li>
 * </ul>
 */
@Repository
public interface AttritionPredictionHistoryRepository
        extends JpaRepository<AttritionPredictionHistory, Long> {

    List<AttritionPredictionHistory> findByUserIdOrderByComputedAtDesc(Long userId);

    Optional<AttritionPredictionHistory> findFirstByUserIdOrderByComputedAtDesc(Long userId);

    /**
     * Записи за период — основа выгрузки «неделя / месяц / квартал».
     *
     * <p>{@code [from, toExclusive)}: правая граница не включается, иначе
     * соседние периоды пересекались бы на границе.
     */
    List<AttritionPredictionHistory> findByComputedAtBetweenOrderByComputedAtDesc(
            LocalDateTime fromExclusive, LocalDateTime toExclusive);

    long countByComputedAtBetween(LocalDateTime fromExclusive, LocalDateTime toExclusive);

    /** Последние записи журнала для страницы администрирования. */
    List<AttritionPredictionHistory> findAllByOrderByComputedAtDesc(Pageable pageable);

    Optional<AttritionPredictionHistory> findFirstByOrderByComputedAtDesc();

    long countBySource(PredictionSource source);

    @Query("""
            SELECT h.riskLevel, COUNT(h) FROM AttritionPredictionHistory h
            WHERE h.computedAt >= :from AND h.computedAt < :to
            GROUP BY h.riskLevel
            """)
    List<Object[]> countByLevelInPeriod(@Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    /** Сколько сотрудников вообще имеют историю (для оценки покрытия). */
    @Query("SELECT COUNT(DISTINCT h.user.id) FROM AttritionPredictionHistory h")
    long countDistinctUsers();

    /** Сотрудники без единого снимка — их нечего показывать в тренде. */
    @Query("""
            SELECT u.id FROM User u
            WHERE u.active = true AND u.role = com.staffsense.demo.model.Role.EMPLOYEE
              AND NOT EXISTS (SELECT 1 FROM AttritionPredictionHistory h WHERE h.user = u)
            """)
    List<Long> findActiveEmployeesWithoutHistory();

    /** Проверка наличия уровня в периоде — для тестов и отчётов. */
    long countByRiskLevelAndComputedAtBetween(RiskLevel level,
                                              LocalDateTime fromExclusive,
                                              LocalDateTime toExclusive);
}
