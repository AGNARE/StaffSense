package com.staffsense.demo.api.survey;

import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.QuestionType;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.Survey;
import com.staffsense.demo.model.SurveyQuestion;
import com.staffsense.demo.model.SurveyStatus;
import com.staffsense.demo.model.SurveyType;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт опросов.
 *
 * <p>Проверяем форму прохождения (вопросы и порядок), закрытость результатов
 * от рядового сотрудника и то, что опрос без ответов отдаёт пустую сводку,
 * а не падает.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SurveyApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SurveyRepository surveyRepository;

    @Autowired
    private SurveyQuestionRepository questionRepository;

    @Autowired
    private UserRepository userRepository;

    private Survey survey;
    private SurveyQuestion first;

    @BeforeEach
    void setUp() {
        User admin = userRepository.findByEmail("surv.admin@test.local").orElseGet(
                () -> userRepository.save(user("surv.admin@test.local", "Админ Опросов", Role.ADMIN)));
        userRepository.findByEmail("surv.emp@test.local").orElseGet(
                () -> userRepository.save(user("surv.emp@test.local", "Сотрудник Опросов", Role.EMPLOYEE)));

        survey = surveyRepository.save(Survey.builder()
                .title("Тестовый опрос API")
                .surveyType(SurveyType.CUSTOM)
                .anonymous(true)
                .status(SurveyStatus.CLOSED)
                .createdBy(admin)
                .targetScope(com.staffsense.demo.model.TargetScope.COMPANY)
                .startDate(LocalDate.now().minusDays(10))
                .endDate(LocalDate.now().minusDays(1))
                .build());

        first = questionRepository.save(SurveyQuestion.builder()
                .survey(survey)
                .text("Насколько вы довольны?")
                .type(QuestionType.LIKERT_5)
                .orderIndex(1)
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Форма опроса содержит вопросы и признак анонимности")
    void formContainsQuestions() throws Exception {
        authenticate("surv.emp@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/surveys/" + survey.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Тестовый опрос API"))
                .andExpect(jsonPath("$.anonymous").value(true))
                .andExpect(jsonPath("$.questions[*].text").value(hasItem("Насколько вы довольны?")))
                // опрос закрыт — проходить нельзя
                .andExpect(jsonPath("$.canTake").value(false));
    }

    @Test
    @DisplayName("Результаты закрыты от рядового сотрудника")
    void resultsForbiddenForEmployee() throws Exception {
        authenticate("surv.emp@test.local", "ROLE_EMPLOYEE");

        mockMvc.perform(get("/api/v1/surveys/" + survey.getId() + "/results"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Опрос без ответов отдаёт пустую сводку, а не ошибку")
    void resultsAreEmptyButValid() throws Exception {
        authenticate("surv.admin@test.local", "ROLE_ADMIN");

        mockMvc.perform(get("/api/v1/surveys/" + survey.getId() + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResponses").value(0))
                .andExpect(jsonPath("$.anonymous").value(true));
    }

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
                .department("HR")
                .position("Specialist")
                .salary(new BigDecimal("85000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2023, 2, 1))
                .active(true)
                .build();
    }
}
