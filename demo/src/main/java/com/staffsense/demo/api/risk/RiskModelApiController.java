package com.staffsense.demo.api.risk;

import com.staffsense.demo.service.metrics.ModelInsightService;
import com.staffsense.demo.service.risk.RiskThresholdService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Параметры действующей ML-модели для экрана «ML-модель».
 *
 * <p>Отдаёт то, что модель реально использует: коэффициенты признаков и
 * действующие пороги уровней. Содержимое идёт из ML-сервиса
 * ({@code /api/v1/model-info}) через {@link ModelInsightService}; если сервис
 * недоступен, сервис подставляет экспертную оценку и честно помечает источник
 * как {@code EXPERT} — экран показывает это же.
 */
@RestController
@RequestMapping("/api/v1/risk/model")
@Tag(name = "ML-модель", description = "Коэффициенты и пороги действующей модели")
public class RiskModelApiController {

    private final ModelInsightService modelInsightService;
    private final RiskThresholdService thresholdService;

    public RiskModelApiController(ModelInsightService modelInsightService,
                                  RiskThresholdService thresholdService) {
        this.modelInsightService = modelInsightService;
        this.thresholdService = thresholdService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR','EXEC','PM')")
    public ModelDto model() {
        List<FeatureDto> features = modelInsightService.getFeatureWeights().stream()
                .map(w -> new FeatureDto(
                        w.getId(),
                        w.getLabel(),
                        w.getWeight(),
                        w.getImportance(),
                        w.getDescription(),
                        w.getSource()))
                .toList();

        return new ModelDto(
                thresholdService.getLow(),
                thresholdService.getMedium(),
                thresholdService.isUsingDefaults(),
                features);
    }

    /** Один коэффициент: сырой вес и относительная значимость 0..1. */
    public record FeatureDto(
            String id,
            String label,
            double weight,
            double importance,
            String description,
            String source
    ) {
    }

    /** Коэффициенты и пороги. */
    public record ModelDto(
            double low,
            double medium,
            boolean thresholdsFromConfig,
            List<FeatureDto> features
    ) {
    }
}
