package com.staffsense.demo.service.risk;

import com.staffsense.demo.config.RiskThresholdProperties;
import com.staffsense.demo.model.RiskLevel;
import com.staffsense.demo.model.RiskThresholdSetting;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.AttritionPredictionRepository;
import com.staffsense.demo.repository.RiskThresholdSettingRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Настройка порогов уровней риска (FR-039, BR-007).
 *
 * <p>Что было: пороги жили только в {@code application.yml} и менялись правкой
 * файла с перезапуском. Требование FR-039 предполагает настройку из интерфейса,
 * поэтому значения хранятся в таблице {@code risk_threshold_setting}, а
 * конфигурация остаётся значением по умолчанию.
 *
 * <p>Как это работает без второго источника правды: сервис при старте
 * подставляет сохранённые значения в существующий бин
 * {@link RiskThresholdProperties}, который уже используют все потребители
 * ({@code levelFor} в ML-сервисе, генераторе датасета, инициализаторе).
 * Отдельного «настроечного» пути чтения нет — иначе пороги разъехались бы.
 *
 * <p>Важное следствие: уже сохранённые прогнозы хранят уровень на момент
 * расчёта. После смены порогов они не меняются сами — для этого есть
 * {@link #recomputeStoredLevels()}, который пересчитывает уровень по баллу
 * (журнал истории при этом не трогается: снимок — это снимок).
 */
@Service
public class RiskThresholdService {

    private static final Logger log = LoggerFactory.getLogger(RiskThresholdService.class);

    /** Допустимый диапазон баллов риска. */
    private static final double MIN_SCORE = 0.0;
    private static final double MAX_SCORE = 100.0;

    private final RiskThresholdSettingRepository settingRepository;
    private final AttritionPredictionRepository predictionRepository;
    private final RiskThresholdProperties properties;

    private LocalDateTime updatedAt;

    public RiskThresholdService(RiskThresholdSettingRepository settingRepository,
                                AttritionPredictionRepository predictionRepository,
                                RiskThresholdProperties properties) {
        this.settingRepository = settingRepository;
        this.predictionRepository = predictionRepository;
        this.properties = properties;
    }

    /**
     * Подставляет сохранённые пороги в бин при старте.
     *
     * <p>Если строки нет или значение некорректно (например, база правила
     * вручную), остаются значения из конфигурации — приложение не должно
     * падать из-за настройки.
     */
    @PostConstruct
    public void loadSavedThresholds() {
        try {
            settingRepository.findById(RiskThresholdSetting.SINGLETON_ID).ifPresentOrElse(
                    row -> {
                        try {
                            validate(row.getLowBound(), row.getMediumBound());
                            properties.setLow(row.getLowBound());
                            properties.setMedium(row.getMediumBound());
                            updatedAt = row.getUpdatedAt();
                            log.info("Пороги риска загружены из БД: low={}, medium={}",
                                    row.getLowBound(), row.getMediumBound());
                        } catch (IllegalArgumentException e) {
                            log.warn("Сохранённые пороги некорректны ({}), использую конфигурацию: "
                                    + "low={}, medium={}", e.getMessage(),
                                    properties.getLow(), properties.getMedium());
                        }
                    },
                    () -> log.info("Пороги риска из БД не заданы, использую конфигурацию: "
                            + "low={}, medium={}", properties.getLow(), properties.getMedium()));
        } catch (Exception e) {
            log.warn("Не удалось прочитать пороги из БД: {}. Использую конфигурацию.",
                    e.getMessage());
        }
    }

    public double getLow() {
        return properties.getLow();
    }

    public double getMedium() {
        return properties.getMedium();
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** Действуют ли значения из конфигурации (строка в БД ещё не создана). */
    public boolean isUsingDefaults() {
        return settingRepository.findById(RiskThresholdSetting.SINGLETON_ID).isEmpty();
    }

    /**
     * Сохраняет новые пороги и сразу применяет их к работающему приложению.
     *
     * @throws IllegalArgumentException если нарушено правило {@code 0 < low < medium < 100}
     */
    @Transactional
    public void update(double low, double medium, User actor) {
        validate(low, medium);

        RiskThresholdSetting row = settingRepository
                .findById(RiskThresholdSetting.SINGLETON_ID)
                .orElseGet(() -> {
                    RiskThresholdSetting fresh = new RiskThresholdSetting();
                    fresh.setId(RiskThresholdSetting.SINGLETON_ID);
                    return fresh;
                });

        row.setLowBound(low);
        row.setMediumBound(medium);
        row.setUpdatedAt(LocalDateTime.now());
        row.setUpdatedBy(actor);
        settingRepository.save(row);

        properties.setLow(low);
        properties.setMedium(medium);
        updatedAt = row.getUpdatedAt();

        log.info("Пороги риска изменены: low={}, medium={}, автор={}", low, medium,
                actor != null ? actor.getEmail() : "система");
    }

    /**
     * Пересчитывает сохранённые уровни текущих прогнозов по их баллам.
     *
     * <p>Нужно после смены порогов: иначе в списке останутся «старые» уровни,
     * и руководитель увидит HIGH там, где по новым порогам уже MEDIUM.
     * Журнал истории не изменяется — это снимки прошлых решений.
     *
     * @return сколько записей обновлено
     */
    @Transactional
    public int recomputeStoredLevels() {
        double low = properties.getLow();
        double medium = properties.getMedium();

        int lowCount = predictionRepository.updateLevelInRange(RiskLevel.LOW, MIN_SCORE, low);
        int mediumCount = predictionRepository.updateLevelInRange(RiskLevel.MEDIUM, low, medium);
        int highCount = predictionRepository.updateLevelInRange(RiskLevel.HIGH, medium, MAX_SCORE + 0.001);

        int total = lowCount + mediumCount + highCount;
        log.info("Уровни прогнозов пересчитаны по новым порогам: LOW={}, MEDIUM={}, HIGH={}",
                lowCount, mediumCount, highCount);
        return total;
    }

    /** Правило BR-007. */
    public static void validate(double low, double medium) {
        if (!Double.isFinite(low) || !Double.isFinite(medium)) {
            throw new IllegalArgumentException("Пороги должны быть числами");
        }
        if (low <= MIN_SCORE) {
            throw new IllegalArgumentException(
                    "Нижний порог должен быть больше 0 (получено " + format(low) + ")");
        }
        if (medium >= MAX_SCORE) {
            throw new IllegalArgumentException(
                    "Верхний порог должен быть меньше 100 (получено " + format(medium) + ")");
        }
        if (medium <= low) {
            throw new IllegalArgumentException(
                    "Верхний порог должен быть больше нижнего: " + format(medium)
                            + " ≤ " + format(low));
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.US, "%.1f", value).replace('.', ',');
    }
}
