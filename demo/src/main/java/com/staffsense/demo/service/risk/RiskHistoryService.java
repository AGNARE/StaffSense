package com.staffsense.demo.service.risk;

import com.staffsense.demo.model.AttritionPredictionHistory;
import com.staffsense.demo.model.PredictionSource;
import com.staffsense.demo.repository.AttritionPredictionHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сводка по журналу прогнозов: то, что нужно странице администрирования и
 * выгрузке за период.
 *
 * <p>Журнал ведётся с миграции V9. До неё история не хранилась (BR-010 удалял
 * прежние прогнозы), поэтому «за квартал» раньше означало бы просто текущий срез.
 */
@Service
public class RiskHistoryService {

    /** Итоги по периодам: сколько снимков и как они распределены по уровням. */
    public record PeriodSummary(RiskPeriod period,
                                LocalDateTime from,
                                LocalDateTime to,
                                long total,
                                long low,
                                long medium,
                                long high,
                                long distinctUsers) {

        /** Доля высокого риска среди снимков периода (0, если пусто). */
        public double highShare() {
            return total == 0 ? 0.0 : (double) high / total;
        }

        public boolean isEmpty() {
            return total == 0;
        }
    }

    private final AttritionPredictionHistoryRepository historyRepository;

    public RiskHistoryService(AttritionPredictionHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    @Transactional(readOnly = true)
    public PeriodSummary summary(RiskPeriod period) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = period.from(to);

        long low = 0;
        long medium = 0;
        long high = 0;
        for (Object[] row : historyRepository.countByLevelInPeriod(from, to)) {
            String level = String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            switch (level) {
                case "HIGH" -> high = count;
                case "MEDIUM" -> medium = count;
                case "LOW" -> low = count;
                default -> { /* неизвестный уровень игнорируем */ }
            }
        }

        return new PeriodSummary(period, from, to, low + medium + high,
                low, medium, high, historyRepository.countDistinctUsers());
    }

    /**
     * История сотрудника в порядке возрастания даты — для графика тренда.
     *
     * @param limit сколько последних снимков взять (0 или меньше — все)
     */
    @Transactional(readOnly = true)
    public List<AttritionPredictionHistory> historyAscending(Long userId, int limit) {
        List<AttritionPredictionHistory> rows =
                historyRepository.findByUserIdOrderByComputedAtDesc(userId);
        List<AttritionPredictionHistory> selected = limit > 0 && rows.size() > limit
                ? rows.subList(0, limit)
                : rows;
        List<AttritionPredictionHistory> ascending = new java.util.ArrayList<>(selected);
        java.util.Collections.reverse(ascending);
        return ascending;
    }

    @Transactional(readOnly = true)
    public List<AttritionPredictionHistory> latest(int limit) {
        return historyRepository.findAllByOrderByComputedAtDesc(
                PageRequest.of(0, Math.max(1, limit)));
    }

    @Transactional(readOnly = true)
    public List<AttritionPredictionHistory> inPeriod(RiskPeriod period) {
        LocalDateTime to = LocalDateTime.now();
        return historyRepository.findByComputedAtBetweenOrderByComputedAtDesc(period.from(to), to);
    }

    @Transactional(readOnly = true)
    public long totalSnapshots() {
        return historyRepository.count();
    }

    @Transactional(readOnly = true)
    public long heuristicSnapshots() {
        return historyRepository.countBySource(PredictionSource.HEURISTIC);
    }

    @Transactional(readOnly = true)
    public long seedSnapshots() {
        return historyRepository.countBySource(PredictionSource.SEED);
    }

    @Transactional(readOnly = true)
    public long employeesWithoutHistory() {
        return historyRepository.findActiveEmployeesWithoutHistory().size();
    }

    @Transactional(readOnly = true)
    public LocalDateTime lastComputedAt() {
        return historyRepository.findFirstByOrderByComputedAtDesc()
                .map(AttritionPredictionHistory::getComputedAt)
                .orElse(null);
    }
}
