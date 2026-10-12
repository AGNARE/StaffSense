package com.staffsense.demo.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ERR-008: переполнение очереди фонового пересчёта.
 *
 * <p>У {@code mlTaskExecutor} очередь на 50 задач и {@code AbortPolicy}, поэтому
 * при переполнении {@code @Async}-прокси бросает {@link TaskRejectedException}.
 * Диспетчер обязан превратить это в {@code false} и WARN, а не пробросить
 * исключение в контроллер (иначе пользователь получал 500).
 *
 * <p>Юнит-тест без Spring-контекста: проверяется ровно обработка исключения.
 */
class MlRecomputeDispatcherTest {

    private final AttritionMlService attritionMlService = mock(AttritionMlService.class);
    private final MlRecomputeDispatcher dispatcher = new MlRecomputeDispatcher(attritionMlService);

    @Test
    @DisplayName("Задача поставлена в очередь — true")
    void returnsTrueWhenTaskQueued() {
        when(attritionMlService.computeAllEmployeesAsync())
                .thenReturn(CompletableFuture.completedFuture(null));

        assertThat(dispatcher.dispatchRecomputeAll()).isTrue();
    }

    @Test
    @DisplayName("Очередь переполнена — false и никакого исключения наружу")
    void returnsFalseWhenQueueIsFull() {
        when(attritionMlService.computeAllEmployeesAsync())
                .thenThrow(new TaskRejectedException("Executor [mlTaskExecutor] did not accept task"));

        assertThat(dispatcher.dispatchRecomputeAll())
                .as("переполнение очереди не должно превращаться в 500")
                .isFalse();
    }

    @Test
    @DisplayName("Текст сообщения для пользователя соответствует ERR-008")
    void exposesUserFacingMessage() {
        assertThat(MlRecomputeDispatcher.UNAVAILABLE_MESSAGE)
                .isEqualTo("Пересчёт временно недоступен, повторите позже.");
    }
}
