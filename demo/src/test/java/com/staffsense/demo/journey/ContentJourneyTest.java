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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозной сценарий контента: опросы, статьи портала и справочник.
 *
 * <p>Три пути «действие → последствие»:
 * <ol>
 *   <li>HR создаёт черновик опроса, публикует его — и правка опубликованного
 *       отклоняется конфликтом 409 (BR-013: ответы не должны потерять связь
 *       с вопросами); черновик при этом действительно не изменился;</li>
 *   <li>статья портала создаётся черновиком, сотруднику она не видна,
 *       после публикации — видна и по прямой ссылке, и в списке раздела;</li>
 *   <li>справочник открыт рядовому сотруднику, но 🔴 отдаёт только безопасные
 *       поля: оклада (BR-031) и хеша пароля в ответе нет.</li>
 * </ol>
 *
 * <p>Тест транзакционный: опросы, статьи и тестовые сотрудники откатываются.
 * Отдел у своих сотрудников свой, чтобы счётчики справочника не зависели
 * от демо-дампа и данных других тестов.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContentJourneyTest {

    private static final String HR_EMAIL = "journey.content.hr@test.local";
    private static final String EMPLOYEE_EMAIL = "journey.content.employee@test.local";
    private static final String PM_EMAIL = "journey.content.pm@test.local";

    /** Отдел только для этого класса: фильтр справочника даёт одну строку. */
    private static final String DEPARTMENT = "Отдел контентных путешествий";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private User hr;
    private User employee;

    @BeforeEach
    void setUp() {
        hr = ensureUser(HR_EMAIL, "Кадровик Контентный", Role.HR, null, "Кадровая служба");
        employee = ensureUser(EMPLOYEE_EMAIL, "Сотрудник Контентный", Role.EMPLOYEE, hr, DEPARTMENT);
        ensureUser(PM_EMAIL, "Руководитель Контентный", Role.PM, null, "Руководство");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    // ── Сценарий: черновик опроса → публикация → правка запрещена ────────

    @Test
    @DisplayName("HR публикует опрос, и правка опубликованного отклоняется с 409")
    void publishedSurveyIsNotEditable() throws Exception {
        authenticate(HR_EMAIL, "ROLE_HR");

        // 1. Черновик: два вопроса, статус DRAFT, форма разрешает правку.
        MvcResult created = mockMvc.perform(post("/api/v1/surveys")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Пульс контентных путешествий",
                                  "surveyType": "PULSE",
                                  "targetScope": "COMPANY",
                                  "startDate": "2026-10-20",
                                  "endDate": "2026-10-31",
                                  "anonymous": true,
                                  "questions": [
                                    {"text": "Насколько удобно путешествовать по контенту?", "type": "LIKERT_5", "required": true},
                                    {"text": "Что улучшить в контенте?", "type": "FREE_TEXT", "required": false}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.statusTitle").value("Черновик"))
                .andExpect(jsonPath("$.questionCount").value(2))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.publishable").value(true))
                .andReturn();

        long surveyId = idOf(created);

        // 2. Публикация: опрос активен и больше не редактируется.
        mockMvc.perform(post("/api/v1/surveys/" + surveyId + "/publish").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.statusTitle").value("Активен"))
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.publishable").value(false));

        // 3. Правка опубликованного — конфликт, а не молчаливая потеря ответов.
        mockMvc.perform(put("/api/v1/surveys/" + surveyId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Правка уже опубликованного опроса",
                                  "surveyType": "PULSE",
                                  "targetScope": "COMPANY",
                                  "startDate": "2026-10-20",
                                  "endDate": "2026-10-31",
                                  "questions": [
                                    {"text": "Новый вопрос вместо старых", "type": "LIKERT_5", "required": true}
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("только черновик")));

        // 4. Отказ настоящий: ни название, ни вопросы не изменились.
        mockMvc.perform(get("/api/v1/surveys/" + surveyId + "/builder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Пульс контентных путешествий"))
                .andExpect(jsonPath("$.questionCount").value(2))
                .andExpect(jsonPath("$.questions[0].text")
                        .value("Насколько удобно путешествовать по контенту?"));
    }

    // ── Сценарий: статья-черновик → публикация → видна сотруднику ────────

    @Test
    @DisplayName("Статья создаётся черновиком, скрыта от сотрудника и открывается после публикации")
    void articleIsDraftUntilPublished() throws Exception {
        authenticate(HR_EMAIL, "ROLE_HR");

        // 1. Создание: по умолчанию черновик, публикация — отдельное действие.
        MvcResult created = mockMvc.perform(post("/api/v1/onboarding/articles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"section":"POLICIES","title":"Регламент контентных путешествий",
                                 "content":"Черновик регламента: до публикации сотрудникам не виден."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.section").value("POLICIES"))
                .andExpect(jsonPath("$.title").value("Регламент контентных путешествий"))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.statusTitle").value("Черновик"))
                .andReturn();

        long articleId = idOf(created);

        // 2. Сотрудник черновик не видит — даже по прямой ссылке (404).
        authenticate(EMPLOYEE_EMAIL, "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/onboarding/articles/" + articleId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/onboarding/articles").param("section", "POLICIES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(
                        hasItem((int) articleId))));

        // 3. HR публикует статью.
        authenticate(HR_EMAIL, "ROLE_HR");

        mockMvc.perform(post("/api/v1/onboarding/articles/" + articleId + "/publish").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.statusTitle").value("Опубликована"));

        // 4. Последствие: сотрудник открывает статью и видит её в списке раздела.
        authenticate(EMPLOYEE_EMAIL, "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/onboarding/articles/" + articleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Регламент контентных путешествий"))
                .andExpect(jsonPath("$.section").value("POLICIES"));

        mockMvc.perform(get("/api/v1/onboarding/articles").param("section", "POLICIES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem((int) articleId)));
    }

    // ── Сценарий: справочник открыт, но без оклада ───────────────────────

    @Test
    @DisplayName("Справочник доступен сотруднику и не содержит поля salary")
    void directoryIsOpenButHasNoSalary() throws Exception {
        authenticate(EMPLOYEE_EMAIL, "ROLE_EMPLOYEE");

        MvcResult result = mockMvc.perform(get("/api/v1/directory").param("department", DEPARTMENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                // В отделе этого класса ровно один сотрудник.
                .andExpect(jsonPath("$.shown").value(1))
                .andExpect(jsonPath("$.employees.length()").value(1))
                .andExpect(jsonPath("$.employees[0].id").value(employee.getId()))
                .andExpect(jsonPath("$.employees[0].fullName").value("Сотрудник Контентный"))
                .andExpect(jsonPath("$.employees[0].email").value(EMPLOYEE_EMAIL))
                .andExpect(jsonPath("$.employees[0].department").value(DEPARTMENT))
                // 🔴 BR-031: полей вне безопасного набора в контракте нет.
                .andExpect(jsonPath("$.employees[0].salary").doesNotExist())
                .andExpect(jsonPath("$.employees[0].passwordHash").doesNotExist())
                .andReturn();

        // Явный UTF-8: в Content-Type нет charset, и кириллица иначе
        // прочиталась бы как ISO-8859-1.
        String json = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertFalse(json.contains("\"salary\""), "в справочнике не должно быть поля salary");
        assertFalse(json.contains("passwordHash"), "справочник не должен отдавать хеш пароля");
        // Безопасные поля при этом на месте — контракт не «обеднел» до id.
        assertTrue(json.contains("managerName"), "руководитель — часть безопасного набора");
        assertTrue(json.contains(DEPARTMENT), "подразделение — часть безопасного набора");
    }

    // ── вспомогательное ──────────────────────────────────────────────────

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

    private User ensureUser(String email, String fullName, Role role, User manager, String department) {
        User user = userRepository.findByEmail(email).orElseGet(User::new);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setDepartment(department);
        user.setPosition("Specialist");
        user.setSalary(new BigDecimal("87000"));
        user.setGradeLevel(GradeLevel.MIDDLE);
        user.setWeeklyCapacityHours(40);
        user.setHireDate(LocalDate.of(2023, 7, 10));
        user.setActive(true);
        user.setManager(manager);
        return userRepository.save(user);
    }
}
