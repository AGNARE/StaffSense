package com.staffsense.demo.api.risk;

import com.staffsense.demo.model.AttritionPrediction;
import com.staffsense.demo.model.AttritionPredictionHistory;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.PredictionSource;
import com.staffsense.demo.model.RiskLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.AttritionPredictionHistoryRepository;
import com.staffsense.demo.repository.AttritionPredictionRepository;
import com.staffsense.demo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт {@code /api/v1/risk}.
 *
 * <p>Проверяем не «что-то вернулось», а свойства, на которые будет опираться
 * клиент: сортировка по убыванию риска, форма страницы, наличие признаков и
 * тренда, отдельная ручка порогов и закрытость от рядового сотрудника.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RiskApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AttritionPredictionRepository predictionRepository;

    @Autowired
    private AttritionPredictionHistoryRepository historyRepository;

    private User employeeHigh;
    private User employeeLow;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail("api.hr@test.local").orElseGet(
                () -> userRepository.save(user("api.hr@test.local", "HR", Role.HR)));
        userRepository.findByEmail("api.employee@test.local").orElseGet(
                () -> userRepository.save(user("api.employee@test.local", "Сотрудник",
                        Role.EMPLOYEE)));

        employeeHigh = userRepository.findByEmail("api.high@test.local").orElseGet(
                () -> userRepository.save(user("api.high@test.local", "Высокий Риск",
                        Role.EMPLOYEE)));
        employeeLow = userRepository.findByEmail("api.low@test.local").orElseGet(
                () -> userRepository.save(user("api.low@test.local", "Низкий Риск",
                        Role.EMPLOYEE)));

        if (predictionRepository.findFirstByUserIdOrderByComputedAtDesc(employeeHigh.getId()).isEmpty()) {
            predictionRepository.save(prediction(employeeHigh, 82.0, RiskLevel.HIGH));
            predictionRepository.save(prediction(employeeLow, 21.0, RiskLevel.LOW));

            historyRepository.save(history(employeeHigh, 70.0, 90));
            historyRepository.save(history(employeeHigh, 76.0, 30));
            historyRepository.save(history(employeeHigh, 82.0, 0));
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Список отдаёт страницу с полями контракта и сортировкой по риску")
    void listReturnsPageSortedByRisk() throws Exception {
        authenticate("api.hr@test.local", "ROLE_HR");

        mockMvc.perform(get("/api/v1/risk").param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].riskScore").value(greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(200));
    }

    @Test
    @DisplayName("Верхний элемент списка — самый рискованный сотрудник")
    void highestRiskComesFirst() throws Exception {
        authenticate("api.hr@test.local", "ROLE_HR");

        mockMvc.perform(get("/api/v1/risk").param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.content[0].fullName").value("Высокий Риск"));
    }

    @Test
    @DisplayName("Карточка содержит признаки, тренд и уровень риска")
    void detailContainsFeaturesAndTrend() throws Exception {
        authenticate("api.hr@test.local", "ROLE_HR");

        mockMvc.perform(get("/api/v1/risk/" + employeeHigh.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Высокий Риск"))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.riskScore").value(82.0))
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.trend.points").isArray())
                .andExpect(jsonPath("$.trend.delta").value(12.0));
    }

    @Test
    @DisplayName("Пороги отдаются отдельной ручкой")
    void thresholdsExposed() throws Exception {
        authenticate("api.hr@test.local", "ROLE_HR");

        mockMvc.perform(get("/api/v1/risk/thresholds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.low").exists())
                .andExpect(jsonPath("$.medium").exists())
                .andExpect(jsonPath("$.usingDefaults").exists());
    }

    @Test
    @DisplayName("Рядовой сотрудник в API не проходит")
    void employeeIsForbidden() throws Exception {
        authenticate("api.employee@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/risk"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Неизвестный сотрудник даёт 404, а не 500")
    void unknownEmployeeIsNotFound() throws Exception {
        authenticate("api.hr@test.local", "ROLE_HR");

        mockMvc.perform(get("/api/v1/risk/999999"))
                .andExpect(status().isNotFound());
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private void authenticate(String email, String authority) {
        var token = new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private User user(String email, String fullName, Role role) {
        return User.builder()
                .fullName(fullName)
                .email(email)
                .passwordHash("hash")
                .role(role)
                .department("Engineering")
                .position("Engineer")
                .salary(new BigDecimal("90000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2023, 3, 1))
                .active(true)
                .build();
    }

    private AttritionPrediction prediction(User user, double score, RiskLevel level) {
        AttritionPrediction prediction = AttritionPrediction.builder()
                .user(user)
                .computedAt(LocalDateTime.now())
                .riskScore(score)
                .riskLevel(level)
                .featuresSnapshotJson("{\"salary_ratio\":0.8,\"kpi_norm\":0.4}")
                .topRiskFactorsJson("[{\"factor\":\"Зарплата\",\"impact\":72.5,"
                        + "\"description\":\"Ниже рынка\"}]")
                .build();
        return prediction;
    }

    private AttritionPredictionHistory history(User user, double score, int daysAgo) {
        return AttritionPredictionHistory.builder()
                .user(user)
                .computedAt(LocalDateTime.now().minusDays(daysAgo))
                .riskScore(score)
                .riskLevel(RiskLevel.MEDIUM)
                .source(PredictionSource.ML)
                .build();
    }
}
