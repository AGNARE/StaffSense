package com.staffsense.demo.service.metrics;

import com.staffsense.demo.repository.AttritionPredictionRepository;
import com.staffsense.demo.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FR-018: страница ML-модели показывает веса действующей модели.
 *
 * <p>До правки {@code getFeatureWeights()} возвращал захардкоженный список
 * 1.2/1.0/0.8/0.6/1.1/0.9/0.7 и подписывал его как коэффициенты обученной
 * модели, хотя после StandardScaler коэффициенты имеют порядок 0,1–0,3 и
 * другие знаки. Тест фиксирует три свойства: веса берутся из ML-сервиса,
 * при недоступности честно помечаются как экспертная оценка, и результат
 * кэшируется, чтобы страница не ждала ML при каждой отрисовке.
 */
class ModelInsightServiceWeightsTest {

    private static final String MODEL_INFO_URL = "http://test-ml/api/v1/model-info";

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final ModelInsightService service = new ModelInsightService(
            mock(UserRepository.class),
            mock(AttritionPredictionRepository.class),
            restTemplate,
            MODEL_INFO_URL,
            60_000L);

    @Test
    @DisplayName("Коэффициенты берутся из ML-сервиса и помечаются как TRAINED")
    void usesTrainedCoefficientsFromMlService() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(trainedResponse());

        List<ModelInsightService.FeatureWeight> weights = service.getFeatureWeights();

        assertThat(weights).hasSize(7);
        assertThat(weights).allSatisfy(w -> assertThat(w.getSource())
                .isEqualTo(ModelInsightService.SOURCE_TRAINED));

        // Порядок и значения — как в ответе ML-сервиса, а не экспертные 1.2/1.0/…
        assertThat(weights.get(0).getId()).isEqualTo("salary_ratio");
        assertThat(weights.get(0).getWeight()).isEqualTo(-0.123);
        assertThat(weights.get(2).getId()).isEqualTo("kpi_norm");
        assertThat(weights.get(2).getWeight()).isEqualTo(-0.241);

        // importance нормируется на максимум по модулю: |−0.241| — максимум
        assertThat(weights.get(2).getImportance()).isEqualTo(1.0);
        assertThat(weights.get(5).getImportance()).isCloseTo(0.067 / 0.241, org.assertj.core.data.Offset.offset(1e-6));

        // Описание честно называет коэффициент и направление влияния
        assertThat(weights.get(0).getDescription()).contains("снижает риск").contains("-0.123");
    }

    @Test
    @DisplayName("ML-сервис недоступен — экспертная оценка с пометкой EXPERT")
    void fallsBackToExpertWeightsWhenMlUnavailable() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        List<ModelInsightService.FeatureWeight> weights = service.getFeatureWeights();

        assertThat(weights).hasSize(7);
        assertThat(weights).allSatisfy(w -> {
            assertThat(w.getSource()).isEqualTo(ModelInsightService.SOURCE_EXPERT);
            assertThat(w.getDescription()).contains("экспертная оценка");
        });
        assertThat(weights.get(0).getWeight()).isEqualTo(1.2);
    }

    @Test
    @DisplayName("Неожиданный ответ ML-сервиса — тоже экспертная оценка, без исключения")
    void fallsBackWhenResponseShapeIsUnexpected() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("{\"coefficients\":[]}");

        List<ModelInsightService.FeatureWeight> weights = service.getFeatureWeights();

        assertThat(weights).hasSize(7);
        assertThat(weights.get(0).getSource()).isEqualTo(ModelInsightService.SOURCE_EXPERT);
    }

    @Test
    @DisplayName("Повторный запрос берётся из кэша — ML-сервис вызывается один раз")
    void cachesWeightsBetweenCalls() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(trainedResponse());

        service.getFeatureWeights();
        service.getFeatureWeights();

        verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class));
    }

    private static String trainedResponse() {
        return """
                {
                  "model_loaded": true,
                  "weights_source": "trained",
                  "n_features": 7,
                  "intercept": -0.0152,
                  "coefficients": [
                    {"feature": "salary_ratio", "coefficient": -0.123},
                    {"feature": "workload_index", "coefficient": 0.168},
                    {"feature": "kpi_norm", "coefficient": -0.241},
                    {"feature": "tenure_months_norm", "coefficient": -0.196},
                    {"feature": "satisfaction_norm", "coefficient": -0.191},
                    {"feature": "missed_1on1_ratio", "coefficient": 0.067},
                    {"feature": "overtime_norm", "coefficient": 0.186}
                  ],
                  "risk_threshold_low": 33.0,
                  "risk_threshold_high": 66.0,
                  "version": "2.1.0"
                }
                """;
    }
}
