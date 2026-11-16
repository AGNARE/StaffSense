package com.staffsense.demo.api.survey;

import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.QuestionType;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.Survey;
import com.staffsense.demo.model.SurveyQuestion;
import com.staffsense.demo.model.SurveyStatus;
import com.staffsense.demo.model.SurveyType;
import com.staffsense.demo.model.TargetScope;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.SurveyParticipationRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сценарий сотрудника в опросах: активный опрос виден, ответы принимаются
 * и по старому адресу {@code /responses}, и по {@code /submit}; повторный
 * ответ запрещён.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SurveyEmployeeFlowApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SurveyRepository surveyRepository;

    @Autowired
    private SurveyQuestionRepository questionRepository;

    @Autowired
    private SurveyParticipationRepository participationRepository;

    @Autowired
    private UserRepository userRepository;

    private User employee;
    private Survey survey;
    private SurveyQuestion question;

    @BeforeEach
    void setUp() {
        User hr = ensureUser("survey.flow.hr@test.local", "HR Опросов", Role.HR, "HR");
        employee = ensureUser("survey.flow.emp@test.local", "Сотрудник Опросов", Role.EMPLOYEE, "Engineering");

        // Свой опрос на каждый тест: id новый, поэтому факт участия
        // в предыдущем методе не влияет на доступность.
        survey = surveyRepository.save(Survey.builder()
                .title("Опрос сценария сотрудника")
                .createdBy(hr)
                .targetScope(TargetScope.COMPANY)
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .surveyType(SurveyType.PULSE)
                .anonymous(false)
                .status(SurveyStatus.ACTIVE)
                .build());

        question = questionRepository.save(SurveyQuestion.builder()
                .survey(survey)
                .text("Насколько комфортно на работе?")
                .type(QuestionType.LIKERT_5)
                .orderIndex(1)
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Сотрудник видит активный опрос и может его пройти")
    void employeeSeesActiveSurvey() throws Exception {
        authenticate();

        mockMvc.perform(get("/api/v1/surveys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(survey.getId().intValue())));

        mockMvc.perform(get("/api/v1/surveys/" + survey.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canTake").value(true))
                .andExpect(jsonPath("$.questions[*].id").value(hasItem(question.getId().intValue())));
    }

    @Test
    @DisplayName("Сотрудник отправляет ответы через /submit")
    void employeeCanSubmitAnswers() throws Exception {
        authenticate();

        mockMvc.perform(post("/api/v1/surveys/" + survey.getId() + "/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"" + question.getId() + "\":\"5\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surveyId").value(survey.getId().intValue()))
                .andExpect(jsonPath("$.answered").value(1));

        assertTrue(participationRepository.existsBySurveyIdAndUserId(survey.getId(), employee.getId()),
                "После ответа факт участия должен быть записан");
    }

    @Test
    @DisplayName("Старый адрес /responses продолжает работать и защищает от повтора")
    void responsesAliasStillWorksAndRepeatIsForbidden() throws Exception {
        authenticate();

        mockMvc.perform(post("/api/v1/surveys/" + survey.getId() + "/responses")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"" + question.getId() + "\":\"4\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(1));

        mockMvc.perform(post("/api/v1/surveys/" + survey.getId() + "/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"" + question.getId() + "\":\"5\"}}"))
                .andExpect(status().isForbidden());
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private void authenticate() {
        var token = new UsernamePasswordAuthenticationToken(
                employee.getEmail(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private User ensureUser(String email, String fullName, Role role, String department) {
        return userRepository.findByEmail(email).orElseGet(() -> userRepository.save(
                User.builder()
                        .fullName(fullName)
                        .email(email)
                        .passwordHash("hash")
                        .role(role)
                        .department(department)
                        .position("Specialist")
                        .salary(new BigDecimal("85000"))
                        .gradeLevel(GradeLevel.MIDDLE)
                        .weeklyCapacityHours(40)
                        .hireDate(LocalDate.of(2023, 2, 1))
                        .active(true)
                        .build()));
    }
}
