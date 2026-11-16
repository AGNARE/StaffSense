package com.staffsense.demo.service.risk;

import com.staffsense.demo.config.RiskThresholdProperties;
import com.staffsense.demo.model.RiskLevel;
import com.staffsense.demo.model.RiskThresholdSetting;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.AttritionPredictionRepository;
import com.staffsense.demo.repository.RiskThresholdSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты настройки порогов (FR-039, BR-007).
 *
 * <p>Проверяем три вещи: правило {@code 0 < low < medium < 100}, применение
 * нового значения к работающему приложению (бин общий) и то, что границы
 * уровней не «плавают» на стыках.
 */
class RiskThresholdServiceTest {

    private RiskThresholdSettingRepository settingRepository;
    private AttritionPredictionRepository predictionRepository;
    private RiskThresholdProperties properties;
    private RiskThresholdService service;

    @BeforeEach
    void setUp() {
        settingRepository = mock(RiskThresholdSettingRepository.class);
        predictionRepository = mock(AttritionPredictionRepository.class);
        properties = new RiskThresholdProperties();          // значения по умолчанию 33 / 66
        service = new RiskThresholdService(settingRepository, predictionRepository, properties);
    }

    @Test
    @DisplayName("Валидация: нижний порог должен быть больше нуля")
    void lowMustBePositive() {
        assertThatThrownBy(() -> RiskThresholdService.validate(0, 66))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("больше 0");
    }

    @Test
    @DisplayName("Валидация: верхний порог должен быть меньше 100")
    void mediumMustBeBelowHundred() {
        assertThatThrownBy(() -> RiskThresholdService.validate(33, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("меньше 100");
    }

    @Test
    @DisplayName("Валидация: верхний порог должен быть больше нижнего")
    void mediumMustExceedLow() {
        assertThatThrownBy(() -> RiskThresholdService.validate(66, 66))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("больше нижнего");
        assertThatThrownBy(() -> RiskThresholdService.validate(70, 40))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Корректные значения проходят валидацию")
    void validBoundsPass() {
        RiskThresholdService.validate(33, 66);
        RiskThresholdService.validate(0.1, 99.9);
    }

    @Test
    @DisplayName("Сохранение применяет пороги сразу, без перезапуска")
    void updateAppliesImmediately() {
        when(settingRepository.findById(RiskThresholdSetting.SINGLETON_ID))
                .thenReturn(Optional.empty());
        User admin = User.builder().email("admin@staffsense.com").build();

        service.update(25, 60, admin);

        assertThat(service.getLow()).isEqualTo(25);
        assertThat(service.getMedium()).isEqualTo(60);
        verify(settingRepository).save(any(RiskThresholdSetting.class));

        // Уровни пересчитываются по новым границам
        assertThat(properties.levelFor(24.9)).isEqualTo(RiskLevel.LOW);
        assertThat(properties.levelFor(25)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(properties.levelFor(59.9)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(properties.levelFor(60)).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("Некорректное значение не сохраняется и не меняет действующие пороги")
    void invalidUpdateDoesNotApply() {
        assertThatThrownBy(() -> service.update(80, 50, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(service.getLow()).isEqualTo(33);
        assertThat(service.getMedium()).isEqualTo(66);
        verify(settingRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("При старте сохранённые пороги подставляются в бин")
    void loadsSavedThresholdsOnStartup() {
        RiskThresholdSetting row = new RiskThresholdSetting();
        row.setId(RiskThresholdSetting.SINGLETON_ID);
        row.setLowBound(20.0);
        row.setMediumBound(55.0);
        row.setUpdatedAt(java.time.LocalDateTime.now());
        when(settingRepository.findById(RiskThresholdSetting.SINGLETON_ID))
                .thenReturn(Optional.of(row));

        service.loadSavedThresholds();

        assertThat(service.getLow()).isEqualTo(20);
        assertThat(service.getMedium()).isEqualTo(55);
        assertThat(service.isUsingDefaults()).isFalse();
    }

    @Test
    @DisplayName("Испорченная строка в БД не ломает старт — берётся конфигурация")
    void corruptRowFallsBackToConfig() {
        RiskThresholdSetting row = new RiskThresholdSetting();
        row.setId(RiskThresholdSetting.SINGLETON_ID);
        row.setLowBound(80.0);
        row.setMediumBound(10.0);       // medium < low — нарушение BR-007
        row.setUpdatedAt(java.time.LocalDateTime.now());
        when(settingRepository.findById(RiskThresholdSetting.SINGLETON_ID))
                .thenReturn(Optional.of(row));

        service.loadSavedThresholds();

        assertThat(service.getLow()).isEqualTo(33);
        assertThat(service.getMedium()).isEqualTo(66);
    }

    @Test
    @DisplayName("Пересчёт уровней обновляет три диапазона по новым порогам")
    void recomputeCoversAllRanges() {
        when(predictionRepository.updateLevelInRange(RiskLevel.LOW, 0.0, 33.0)).thenReturn(4);
        when(predictionRepository.updateLevelInRange(RiskLevel.MEDIUM, 33.0, 66.0)).thenReturn(10);
        when(predictionRepository.updateLevelInRange(RiskLevel.HIGH, 66.0, 100.001)).thenReturn(2);

        int changed = service.recomputeStoredLevels();

        assertThat(changed).isEqualTo(16);
    }
}
