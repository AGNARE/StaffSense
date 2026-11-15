package com.staffsense.demo.api.survey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.QuestionType;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.Survey;
import com.staffsense.demo.model.SurveyQuestion;
import com.staffsense.demo.model.SurveyStatus;
import com.staffsense.demo.model.SurveyType;
import com.staffsense.demo.model.TargetScope;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.SurveyQuestionRepository;
import com.staffsense.demo.repository.SurveyRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт конструктора опросов.
 *
 * <p>Проверяем обещания экрана «Конструктор опроса»: кадровик создаёт опрос
 * с вопросами и признаком обязательности, рядовой сотрудник этого не может,
 * публикация меняет статус, а уже опубликованный опрос не редактируется —
 * иначе ответы потеряли бы связь с вопросами (BR-013).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SurveyBuilderApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SurveyRepository surveyRepository;

    @Autowired
    private SurveyQuestionRepository questionRepository;

    @Autowired
    private UserRepository userRepository;

    private User hr;

    @BeforeEach
    void setUp() {
        hr = userRepository.findByEmail("builder.hr@test.local").orElseGet(
                () -> userRepository.save(user("builder.hr@test.local", "Кадровик Конструктора", Role.HR)));
        userRepository.findByEmail("builder.emp@test.local").orElseGet(
                () -> userRepository.save(user("builder.emp@test.local", "Сотрудник Конструктора", Role.EMPLOYEE)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── создание ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HR создаёт опрос с вопросами — 201, вопросы и обязательность сохранены")
    void hrCreatesSurveyWithQuestions() throws Exception {
        authenticate("builder.hr@test.local", "ROLE_HR");

        String body = """
                {
                  "title": "Pulse-опрос: удовлетворённость командой Q4",
                  "surveyType": "PULSE",
                  "targetScope": "COMPANY",
                  "startDate": "2026-10-20",
                  "endDate": "2026-10-31",
                  "anonymous": true,
                  "questions": [
                    {"text": "Насколько вы удовлетворены работой команды в этом квартале?", "type": "LIKERT_5", "required": true},
                    {"text": "Что мешает вам работать эффективнее?", "type": "FREE_TEXT", "required": false}
                  ]
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/surveys")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.anonymous").value(true))
                .andExpect(jsonPath("$.surveyType").value("PULSE"))
                .andExpect(jsonPath("$.targetScope").value("COMPANY"))
                .andExpect(jsonPath("$.questionCount").value(2))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.publishable").value(true))
                // обязательность и тип вопроса доезжают до ответа как есть
                .andExpect(jsonPath("$.questions[0].type").value("LIKERT_5"))
                .andExpect(jsonPath("$.questions[0].required").value(true))
                .andExpect(jsonPath("$.questions[1].type").value("FREE_TEXT"))
                .andExpect(jsonPath("$.questions[1].required").value(false))
                .andReturn();

        long id = idOf(result);

        Survey saved = surveyRepository.findById(id).orElseThrow();
        assertEquals(SurveyStatus.DRAFT, saved.getStatus(), "новый опрос всегда черновик");
        assertEquals(SurveyType.PULSE, saved.getSurveyType());
        assertEquals(TargetScope.COMPANY, saved.getTargetScope());
        assertTrue(saved.isAnonymous());
        assertEquals(LocalDate.of(2026, 10, 20), saved.getStartDate());

        List<SurveyQuestion> questions = questionRepository.findBySurveyIdOrderByOrderIndex(id);
        assertEquals(2, questions.size(), "вопросы сохранены вместе с опросом");
        assertEquals(Integer.valueOf(0), questions.get(0).getOrderIndex());
        assertEquals(Integer.valueOf(1), questions.get(1).getOrderIndex());
        assertEquals(QuestionType.LIKERT_5, questions.get(0).getType());
        assertTrue(questions.get(0).isRequired());
        assertFalse(questions.get(1).isRequired(), "необязательный вопрос остаётся необязательным");
    }

    @Test
    @DisplayName("EMPLOYEE не может создать опрос — 403")
    void employeeCannotCreateSurvey() throws Exception {
        authenticate("builder.emp@test.local", "ROLE_EMPLOYEE");

        String body = """
                {
                  "title": "Опрос от сотрудника",
                  "surveyType": "CUSTOM",
                  "targetScope": "COMPANY",
                  "startDate": "2026-10-20",
                  "endDate": "2026-10-31",
                  "questions": [{"text": "Вопрос", "type": "LIKERT_5", "required": true}]
                }
                """;

        mockMvc.perform(post("/api/v1/surveys")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Окно дат проверяется: окончание раньше начала — 400")
    void endDateBeforeStartDateIsBadRequest() throws Exception {
        authenticate("builder.hr@test.local", "ROLE_HR");

        String body = """
                {
                  "title": "Опрос с обратными датами",
                  "surveyType": "CUSTOM",
                  "targetScope": "COMPANY",
                  "startDate": "2026-10-31",
                  "endDate": "2026-10-01",
                  "questions": [{"text": "Вопрос", "type": "LIKERT_5", "required": true}]
                }
                """;

        mockMvc.perform(post("/api/v1/surveys")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Дата окончания не может быть раньше даты начала."));
    }

    // ── правка черновика ─────────────────────────────────────────────────

    @Test
    @DisplayName("PUT заменяет вопросы черновика")
    void updateReplacesDraftQuestions() throws Exception {
        authenticate("builder.hr@test.local", "ROLE_HR");
        Survey draft = draftWithQuestions(1, SurveyStatus.DRAFT);

        String body = """
                {
                  "title": "Черновик после правки",
                  "surveyType": "ENGAGEMENT",
                  "targetScope": "DEPARTMENT",
                  "targetDepartment": "Engineering",
                  "startDate": "2026-11-01",
                  "endDate": "2026-11-15",
                  "anonymous": false,
                  "questions": [
                    {"text": "Первый вопрос после правки", "type": "LIKERT_5", "required": true},
                    {"text": "Второй вопрос после правки", "type": "FREE_TEXT", "required": false},
                    {"text": "Третий вопрос после правки", "type": "LIKERT_5", "required": true}
                  ]
                }
                """;

        mockMvc.perform(put("/api/v1/surveys/" + draft.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Черновик после правки"))
                .andExpect(jsonPath("$.targetScope").value("DEPARTMENT"))
                .andExpect(jsonPath("$.targetDepartment").value("Engineering"))
                .andExpect(jsonPath("$.questionCount").value(3))
                .andExpect(jsonPath("$.questions[1].required").value(false));

        List<SurveyQuestion> questions =
                questionRepository.findBySurveyIdOrderByOrderIndex(draft.getId());
        assertEquals(3, questions.size(), "старые вопросы заменены, а не добавлены");
        assertEquals("Первый вопрос после правки", questions.get(0).getText());
        assertEquals(Integer.valueOf(2), questions.get(2).getOrderIndex());
    }

    @Test
    @DisplayName("Опубликованный опрос не редактируется — 409")
    void publishedSurveyIsNotEditable() throws Exception {
        authenticate("builder.hr@test.local", "ROLE_HR");
        Survey active = draftWithQuestions(1, SurveyStatus.ACTIVE);

        String body = """
                {
                  "title": "Правка активного опроса",
                  "surveyType": "CUSTOM",
                  "targetScope": "COMPANY",
                  "startDate": "2026-11-01",
                  "endDate": "2026-11-15",
                  "questions": [{"text": "Новый вопрос", "type": "LIKERT_5", "required": true}]
                }
                """;

        mockMvc.perform(put("/api/v1/surveys/" + active.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // ── публикация ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Публикация меняет статус черновика на ACTIVE")
    void publishChangesStatus() throws Exception {
        authenticate("builder.hr@test.local", "ROLE_HR");
        Survey draft = draftWithQuestions(2, SurveyStatus.DRAFT);

        mockMvc.perform(post("/api/v1/surveys/" + draft.getId() + "/publish").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.statusTitle").value("Активен"))
                .andExpect(jsonPath("$.editable").value(false));

        assertEquals(SurveyStatus.ACTIVE,
                surveyRepository.findById(draft.getId()).orElseThrow().getStatus(),
                "статус сохранён в базе, а не только в ответе");

        // Форма прохождения видит тот же статус — опрос действительно опубликован.
        mockMvc.perform(get("/api/v1/surveys/" + draft.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Опрос без вопросов не публикуется — 409")
    void publishWithoutQuestionsIsConflict() throws Exception {
        authenticate("builder.hr@test.local", "ROLE_HR");
        Survey empty = surveyRepository.save(Survey.builder()
                .title("Пустой черновик")
                .createdBy(hr)
                .targetScope(TargetScope.COMPANY)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(7))
                .surveyType(SurveyType.CUSTOM)
                .anonymous(false)
                .status(SurveyStatus.DRAFT)
                .build());

        mockMvc.perform(post("/api/v1/surveys/" + empty.getId() + "/publish").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertEquals(SurveyStatus.DRAFT,
                surveyRepository.findById(empty.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("EMPLOYEE не может опубликовать опрос — 403")
    void employeeCannotPublish() throws Exception {
        authenticate("builder.hr@test.local", "ROLE_HR");
        Survey draft = draftWithQuestions(1, SurveyStatus.DRAFT);

        authenticate("builder.emp@test.local", "ROLE_EMPLOYEE");
        mockMvc.perform(post("/api/v1/surveys/" + draft.getId() + "/publish").with(csrf()))
                .andExpect(status().isForbidden());

        assertEquals(SurveyStatus.DRAFT,
                surveyRepository.findById(draft.getId()).orElseThrow().getStatus());
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private Survey draftWithQuestions(int count, SurveyStatus status) {
        Survey survey = surveyRepository.save(Survey.builder()
                .title("Черновик конструктора")
                .createdBy(hr)
                .targetScope(TargetScope.COMPANY)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(10))
                .surveyType(SurveyType.CUSTOM)
                .anonymous(false)
                .status(status)
                .build());

        for (int index = 0; index < count; index++) {
            questionRepository.save(SurveyQuestion.builder()
                    .survey(survey)
                    .text("Вопрос " + (index + 1))
                    .type(QuestionType.LIKERT_5)
                    .orderIndex(index)
                    .build());
        }
        return survey;
    }

    private long idOf(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private void authenticate(String email, String authority) {
        SecurityContextHolder.clearContext();
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
                .department("HR")
                .position("Specialist")
                .salary(new BigDecimal("90000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2023, 3, 1))
                .active(true)
                .build();
    }
}
