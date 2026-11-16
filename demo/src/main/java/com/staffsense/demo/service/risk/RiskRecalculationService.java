package com.staffsense.demo.service.risk;

import com.staffsense.demo.service.AttritionMlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Пересчёт риска по событиям (FR-036) с защитой от шторма.
 *
 * <p>Как это работает:
 * <ol>
 *   <li>доменный сервис публикует {@link RiskRecalculationRequested};</li>
 *   <li>слушатель запоминает сотрудника и время последней заявки — не более
 *       одной на сотрудника в окне {@code risk.recalc.cooldown};</li>
 *   <li>планировщик раз в {@code risk.recalc.interval} забирает «созревшие»
 *       заявки и пересчитывает прогноз по одной.</li>
 * </ol>
 *
 * <p>Почему не пересчитывать сразу в обработчике события: сохранение ответа
 * на опрос — это одно действие, а пересчёт требует вызова ML-сервиса и записи
 * в журнал. Прямой вызов в транзакции удлинил бы её и позволил бы одним
 * нажатием породить десяток запросов к модели.
 *
 * <p>Ограничения, зафиксированные осознанно:
 * <ul>
 *   <li>очередь живёт в памяти процесса — при перезапуске несохранённые
 *       заявки теряются (прогноз пересчитается следующим событием или по
 *       расписанию админки);</li>
 *   <li>в кластере каждый узел держал бы свою очередь; для одного экземпляра
 *       этого достаточно, для нескольких нужна общая очередь.</li>
 * </ul>
 */
@Service
public class RiskRecalculationService {

    private static final Logger log = LoggerFactory.getLogger(RiskRecalculationService.class);

    private final AttritionMlService mlService;

    /** userId → момент, после которого заявку можно обрабатывать. */
    private final Map<Long, Pending> pending = new ConcurrentHashMap<>();

    private final Duration cooldown;

    public RiskRecalculationService(AttritionMlService mlService,
                                    @Value("${risk.recalc.cooldown:PT5M}") Duration cooldown) {
        this.mlService = mlService;
        this.cooldown = cooldown;
    }

    private record Pending(Instant notBefore, String reason) {
    }

    /** Регистрирует заявку; повторные заявки в пределах окна схлопываются. */
    @EventListener
    public void onRequested(RiskRecalculationRequested event) {
        if (event == null || event.userId() == null) {
            return;
        }
        Instant now = Instant.now();
        pending.merge(event.userId(),
                new Pending(now.plus(cooldown), event.reason()),
                (existing, incoming) -> new Pending(
                        // окно не сдвигаем: иначе непрерывные события не дадут
                        // пересчитать прогноз никогда
                        existing.notBefore(),
                        incoming.reason()));
        log.debug("Заявка на пересчёт риска userId={} причина={}", event.userId(), event.reason());
    }

    /** Сколько заявок ждёт обработки — для тестов и диагностики. */
    public int pendingCount() {
        return pending.size();
    }

    /**
     * Обрабатывает созревшие заявки. Интервал и включение задаются
     * свойствами, чтобы на стендах можно было выключить фоновую работу.
     */
    @Scheduled(fixedDelayString = "${risk.recalc.interval:PT1M}")
    public void flush() {
        Instant now = Instant.now();
        for (Map.Entry<Long, Pending> entry : pending.entrySet()) {
            if (entry.getValue().notBefore().isAfter(now)) {
                continue;
            }
            Long userId = entry.getKey();
            if (pending.remove(userId, entry.getValue())) {
                recalculate(userId, entry.getValue().reason());
            }
        }
    }

    /** Пересчёт одного сотрудника; ошибка не роняет обработку остальных. */
    public void recalculate(Long userId, String reason) {
        try {
            mlService.computeAndSave(userId);
            log.info("Риск пересчитан по событию: userId={} причина={}", userId, reason);
        } catch (Exception e) {
            log.warn("Не удалось пересчитать риск по событию userId={}: {}", userId, e.getMessage());
        }
    }
}
