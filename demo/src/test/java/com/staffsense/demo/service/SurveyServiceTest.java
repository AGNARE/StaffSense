package com.staffsense.demo.service;

import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR-022 и ERR-012: предусловия прохождения опроса.
 *
 * <p>До правки страница прохождения опроса и отправка ответов не проверяли ничего:
 * прямой переход по адресу открывал черновик, закрытый опрос, опрос вне периода
 * и опрос чужой области охвата. Анонимный опрос можно было пройти сколько угодно
 * раз, потому что ответ не связывается с сотрудником (BR-011) и проверка
 * «уже отвечал» по таблице ответов не срабатывала. Это искажало результаты
 * и аналитику выгорания.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SurveyServiceTest {

    @Autowired
    private SurveyService surveyService;

    @Autowired
    private SurveyRepository surveyRepository;

    @Autowired
    private SurveyQuestionRepository questionRepository;

    @Autowired
    private SurveyAnswerRepository answerRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Активный опрос в периоде и в охвате — доступен")
    void activeSurveyIsAvailable() {
        Survey survey = activeCompanySurvey(false);

        assertDoesNotThrow(() -> surveyService.requireCanTake(survey, employee()));
    }

    @Test
    @DisplayName("Черновик и закрытый опрос недоступны")
    void draftAndClosedAreUnavailable() {
        User employee = employee();
        Survey draft = activeCompanySurvey(false);
        draft.setStatus(SurveyStatus.DRAFT);
        Survey closed = activeCompanySurvey(false);
        closed.setStatus(SurveyStatus.CLOSED);

        assertThrows(AccessDeniedException.class, () -> surveyService.requireCanTake(draft, employee));
        assertThrows(AccessDeniedException.class, () -> surveyService.requireCanTake(closed, employee));
    }

    @Test
    @DisplayName("Опрос вне периода проведения недоступен")
    void surveyOutsideWindowIsUnavailable() {
        Survey past = newSurvey(false, LocalDate.now().minusDays(10), LocalDate.now().minusDays(5));

        assertThrows(AccessDeniedException.class, () -> surveyService.requireCanTake(past, employee()));
    }

    @Test
    @DisplayName("Опрос, адресованный другому сотруднику, недоступен")
    void surveyTargetedAtAnotherUserIsUnavailable() {
        Survey personal = newSurvey(false, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        personal.setTargetScope(TargetScope.INDIVIDUAL);
        personal.setTargetUserId(admin().getId());
        surveyRepository.save(personal);

        assertThrows(AccessDeniedException.class, () -> surveyService.requireCanTake(personal, employee()));
    }

    @Test
    @DisplayName("Анонимный опрос нельзя пройти дважды — участие фиксируется отдельно")
    void anonymousSurveyCannotBeTakenTwice() {
        User employee = employee();
        Survey survey = activeCompanySurvey(true);
        SurveyQuestion question = addQuestion(survey);

        surveyService.submitResponse(survey, employee, Map.of(question.getId(), "5"));

        assertThrows(AccessDeniedException.class, () -> surveyService.requireCanTake(survey, employee));
        assertFalse(surveyService.getVisibleForEmployee(employee).stream()
                        .anyMatch(s -> s.getId().equals(survey.getId())),
                "пройденный опрос не должен оставаться в списке доступных");
    }

    @Test
    @DisplayName("Пройденный именной опрос тоже недоступен повторно")
    void namedSurveyCannotBeTakenTwice() {
        User employee = employee();
        Survey survey = activeCompanySurvey(false);
        SurveyQuestion question = addQuestion(survey);

        surveyService.submitResponse(survey, employee, Map.of(question.getId(), "4"));

        assertThrows(AccessDeniedException.class, () -> surveyService.requireCanTake(survey, employee));
    }

    @Test
    @DisplayName("Ответ на вопрос чужого опроса игнорируется")
    void answerToForeignQuestionIsIgnored() {
        User employee = employee();
        Survey own = activeCompanySurvey(false);
        SurveyQuestion ownQuestion = addQuestion(own);
        Survey foreign = activeCompanySurvey(false);
        SurveyQuestion foreignQuestion = addQuestion(foreign);

        long answersBefore = answerRepository.count();
        surveyService.submitResponse(own, employee,
                Map.of(ownQuestion.getId(), "4", foreignQuestion.getId(), "5"));

        assertEquals(answersBefore + 1, answerRepository.count(),
                "сохраняется только ответ на вопрос своего опроса");
    }

    @Test
    @DisplayName("Участие записывается в отдельную таблицу")
    void participationIsRecorded() {
        User employee = employee();
        Survey survey = activeCompanySurvey(true);
        SurveyQuestion question = addQuestion(survey);

        assertTrue(surveyService.getVisibleForEmployee(employee).stream()
                .anyMatch(s -> s.getId().equals(survey.getId())));

        surveyService.submitResponse(survey, employee, Map.of(question.getId(), "5"));

        assertTrue(surveyService.getVisibleForEmployee(employee).stream()
                .noneMatch(s -> s.getId().equals(survey.getId())));
    }

    // ── вспомогательные методы ───────────────────────────────────────────────

    private User admin() {
        return userRepository.findByEmail("admin@staffsense.com").orElseThrow();
    }

    private User employee() {
        return userRepository.findAll().stream()
                .filter(u -> u.isActive() && u.getRole() == Role.EMPLOYEE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("В тестовых данных нет активного сотрудника"));
    }

    private Survey activeCompanySurvey(boolean anonymous) {
        Survey survey = newSurvey(anonymous, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        survey.setStatus(SurveyStatus.ACTIVE);
        return surveyRepository.save(survey);
    }

    private Survey newSurvey(boolean anonymous, LocalDate start, LocalDate end) {
        Survey survey = new Survey();
        survey.setTitle("Тестовый опрос " + System.nanoTime());
        survey.setCreatedBy(admin());
        survey.setTargetScope(TargetScope.COMPANY);
        survey.setStartDate(start);
        survey.setEndDate(end);
        survey.setAnonymous(anonymous);
        return surveyRepository.save(survey);
    }

    private SurveyQuestion addQuestion(Survey survey) {
        SurveyQuestion question = new SurveyQuestion();
        question.setSurvey(survey);
        question.setText("Насколько вы удовлетворены работой?");
        question.setType(QuestionType.LIKERT_5);
        question.setOrderIndex(0);
        return questionRepository.save(question);
    }
}
