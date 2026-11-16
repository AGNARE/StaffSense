package com.staffsense.demo.service.risk;

import com.staffsense.demo.service.AttritionMlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Пересчёт риска по событиям (FR-036).
 *
 * <p>Главное свойство — защита от шторма: десять событий по одному сотруднику
 * не должны дать десять вызовов модели, а событие в пределах окна не должно
 * откладываться бесконечно.
 */
class RiskRecalculationServiceTest {

    private final AttritionMlService mlService = mock(AttritionMlService.class);

    @Test
    @DisplayName("События по одному сотруднику схлопываются в одну заявку")
    void eventsAreCoalesced() {
        RiskRecalculationService service = new RiskRecalculationService(mlService, Duration.ofMinutes(5));

        for (int i = 0; i < 10; i++) {
            service.onRequested(new RiskRecalculationRequested(7L, "событие " + i));
        }

        assertThat(service.pendingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("До истечения окна пересчёт не выполняется")
    void noRecalculationWithinCooldown() {
        RiskRecalculationService service = new RiskRecalculationService(mlService, Duration.ofMinutes(5));
        service.onRequested(new RiskRecalculationRequested(7L, "опрос"));

        service.flush();

        verify(mlService, never()).computeAndSave(7L);
        assertThat(service.pendingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("После истечения окна заявка обрабатывается один раз")
    void recalculatesAfterCooldown() {
        // нулевое окно — событие созревает сразу
        RiskRecalculationService service = new RiskRecalculationService(mlService, Duration.ZERO);
        service.onRequested(new RiskRecalculationRequested(7L, "опрос"));

        service.flush();

        verify(mlService, times(1)).computeAndSave(7L);
        assertThat(service.pendingCount()).isZero();
    }

    @Test
    @DisplayName("Ошибка ML не мешает обработке остальных заявок")
    void failureDoesNotStopOthers() {
        RiskRecalculationService service = new RiskRecalculationService(mlService, Duration.ZERO);
        org.mockito.Mockito.doThrow(new RuntimeException("ML недоступен"))
                .when(mlService).computeAndSave(5L);

        service.onRequested(new RiskRecalculationRequested(5L, "опрос"));
        service.onRequested(new RiskRecalculationRequested(6L, "опрос"));
        service.flush();

        verify(mlService).computeAndSave(6L);
        assertThat(service.pendingCount()).isZero();
    }
}
