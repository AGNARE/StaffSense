package com.staffsense.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

/**
 * Постановка фонового пересчёта ML-прогнозов в очередь (ERR-008).
 *
 * <p>У исполнителя {@code mlTaskExecutor} очередь на 50 задач и политика отказа
 * {@code AbortPolicy}: при переполнении {@code @Async}-прокси бросает
 * {@link TaskRejectedException} прямо в потоке вызова. До этой правки исключение
 * никто не ловил, поэтому вместо сообщения «Пересчёт временно недоступен,
 * повторите позже» пользователь получал 500.
 *
 * <p>Отдельный бин, а не метод в {@link AttritionMlService}, нужен из-за
 * особенностей прокси: вызов {@code computeAllEmployeesAsync()} из другого метода
 * того же бина идёт мимо прокси, выполняется синхронно и никогда не бросает
 * {@code TaskRejectedException} — обработка оказалась бы мёртвой.
 */
@Component
public class MlRecomputeDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MlRecomputeDispatcher.class);

    /** Сообщение для пользователя при переполнении очереди (ERR-008). */
    public static final String UNAVAILABLE_MESSAGE = "Пересчёт временно недоступен, повторите позже.";

    private final AttritionMlService attritionMlService;

    public MlRecomputeDispatcher(AttritionMlService attritionMlService) {
        this.attritionMlService = attritionMlService;
    }

    /**
     * Ставит задачу пересчёта в очередь.
     *
     * @return {@code true}, если задача поставлена; {@code false}, если очередь
     *         переполнена и пересчёт не стартовал
     */
    public boolean dispatchRecomputeAll() {
        try {
            attritionMlService.computeAllEmployeesAsync();
            return true;
        } catch (TaskRejectedException ex) {
            log.warn("Очередь фонового пересчёта ML переполнена, пересчёт не запущен: {}", ex.getMessage());
            return false;
        }
    }
}
