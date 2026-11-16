package com.staffsense.demo.journey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозной сценарий найма: вакансия → кандидат → этапы → найм.
 *
 * <p>Проверяем воронку как путь, а не как набор ручек:
 * <ol>
 *   <li>HR открывает вакансию на одно место, добавляет кандидата и ведёт его
 *       по этапам до «Нанят» — вакансия закрывается сама, потому что мест
 *       больше не осталось;</li>
 *   <li>перескок через этап отклоняется конфликтом 409, кандидат остаётся
 *       на прежнем этапе (отказ ничего не сломал);</li>
 *   <li>в закрытую вакансию нового кандидата добавить нельзя — 409, и воронка
 *       не растёт.</li>
 * </ol>
 *
 * <p>Тест транзакционный: вакансии и кандидаты откатываются, поэтому прогон
 * не оставляет записей в общей тестовой базе.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HiringJourneyTest {

    private static final String HR_EMAIL = "journey.hiring.hr@test.local";
    private static final String DEPARTMENT = "Отдел найма путешественников";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private User hr;

    @BeforeEach
    void setUp() {
        hr = ensureUser(HR_EMAIL, "Рекрутер Путешествий", Role.HR);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    // ── Сценарий: полный проход по воронке ───────────────────────────────

    @Test
    @DisplayName("Кандидат проходит все этапы до найма — вакансия закрывается")
    void candidateGoesThroughFunnelAndClosesVacancy() throws Exception {
        authenticate(HR_EMAIL, "ROLE_HR");

        // 1. HR открывает вакансию на одно место.
        MvcResult vacancyResult = mockMvc.perform(post("/api/v1/hiring/vacancies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Инженер путешествий","department":"%s",
                                 "gradeLevel":"MIDDLE","openings":1}
                                """.formatted(DEPARTMENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Инженер путешествий"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.openings").value(1))
                .andReturn();

        long vacancyId = idOf(vacancyResult);

        // 2. Добавляет кандидата: он всегда попадает на первый этап.
        MvcResult candidateResult = mockMvc.perform(
                        post("/api/v1/hiring/vacancies/" + vacancyId + "/candidates")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"fullName":"Кандидат Дорожный","email":"journey.candidate@test.local",
                                         "source":"hh"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vacancyId").value(vacancyId))
                .andExpect(jsonPath("$.stage").value("APPLIED"))
                .andExpect(jsonPath("$.email").value("journey.candidate@test.local"))
                .andReturn();

        long candidateId = idOf(candidateResult);

        // 3. Ведёт по этапам строго по маршруту.
        moveStage(candidateId, "SCREENING", "SCREENING");
        moveStage(candidateId, "INTERVIEW", "INTERVIEW");
        moveStage(candidateId, "OFFER", "OFFER");
        moveStage(candidateId, "HIRED", "HIRED");

        // 4. Последствие найма: последнее место занято, вакансия закрыта.
        mockMvc.perform(get("/api/v1/hiring/vacancies/" + vacancyId + "/funnel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vacancyId").value(vacancyId))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.hired").value(1))
                .andExpect(jsonPath("$.byStage.HIRED").value(1))
                .andExpect(jsonPath("$.byStage.APPLIED").value(0))
                .andExpect(jsonPath("$.totalCandidates").value(1))
                .andExpect(jsonPath("$.candidates[0].stage").value("HIRED"))
                .andExpect(jsonPath("$.averageDaysToHire").isNumber());

        mockMvc.perform(get("/api/v1/hiring/vacancies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + vacancyId + ")].status")
                        .value(hasItem("CLOSED")));
    }

    // ── Сценарий: перескок через этап ────────────────────────────────────

    @Test
    @DisplayName("Перескок через этап отклоняется с 409, кандидат остаётся на месте")
    void skippingStageIsRejected() throws Exception {
        authenticate(HR_EMAIL, "ROLE_HR");

        long vacancyId = openVacancy("Аналитик путешествий", 1);
        long candidateId = addCandidate(vacancyId, "Кандидат Прямиков");

        // APPLIED → OFFER — два этапа за раз: маршрут такого не разрешает.
        mockMvc.perform(post("/api/v1/hiring/candidates/" + candidateId + "/stage")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"OFFER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("недопустим")));

        // Отказ ничего не сломал: этап прежний, вакансия по-прежнему открыта.
        mockMvc.perform(get("/api/v1/hiring/vacancies/" + vacancyId + "/funnel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.byStage.APPLIED").value(1))
                .andExpect(jsonPath("$.byStage.OFFER").value(0))
                .andExpect(jsonPath("$.candidates[0].stage").value("APPLIED"));
    }

    // ── Сценарий: кандидат в закрытую вакансию ───────────────────────────

    @Test
    @DisplayName("Кандидат в закрытую вакансию не добавляется — 409")
    void candidateIntoClosedVacancyIsRejected() throws Exception {
        authenticate(HR_EMAIL, "ROLE_HR");

        long vacancyId = openVacancy("Разработчик путешествий", 1);
        long candidateId = addCandidate(vacancyId, "Кандидат Закрывающий");

        // Нанятый кандидат закрывает вакансию — мест больше нет.
        moveStage(candidateId, "SCREENING", "SCREENING");
        moveStage(candidateId, "INTERVIEW", "INTERVIEW");
        moveStage(candidateId, "OFFER", "OFFER");
        moveStage(candidateId, "HIRED", "HIRED");

        mockMvc.perform(post("/api/v1/hiring/vacancies/" + vacancyId + "/candidates")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Поздний Кандидат","email":"journey.late.candidate@test.local"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("не принимает")));

        // Воронка не выросла: отклик не сохранился.
        mockMvc.perform(get("/api/v1/hiring/vacancies/" + vacancyId + "/funnel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.totalCandidates").value(1));
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private void moveStage(long candidateId, String stage, String expectedStage) throws Exception {
        mockMvc.perform(post("/api/v1/hiring/candidates/" + candidateId + "/stage")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"" + stage + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value(expectedStage));
    }

    private long openVacancy(String title, int openings) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/hiring/vacancies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","department":"%s","gradeLevel":"MIDDLE","openings":%d}
                                """.formatted(title, DEPARTMENT, openings)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        return idOf(result);
    }

    private long addCandidate(long vacancyId, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/hiring/vacancies/" + vacancyId + "/candidates")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"fullName":"%s","source":"career-site"}
                                        """.formatted(fullName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("APPLIED"))
                .andReturn();
        return idOf(result);
    }

    private long idOf(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private void authenticate(String email, String authority) {
        var token = new UsernamePasswordAuthenticationToken(email, "n/a",
                List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(token);
        // MockMvc помнит контекст первого запроса теста, поэтому смена роли
        // посреди сценария обновляет и test-хранилище Security.
        TestSecurityContextHolder.setAuthentication(token);
    }

    private User ensureUser(String email, String fullName, Role role) {
        User user = userRepository.findByEmail(email).orElseGet(User::new);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setDepartment("HR");
        user.setPosition("Recruiter");
        user.setSalary(new BigDecimal("95000"));
        user.setGradeLevel(GradeLevel.SENIOR);
        user.setWeeklyCapacityHours(40);
        user.setHireDate(LocalDate.of(2023, 3, 6));
        user.setActive(true);
        user.setManager(null);
        return userRepository.save(user);
    }
}
