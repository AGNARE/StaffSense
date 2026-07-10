package com.staffsense.demo.service;

import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SurveyService {

    private final SurveyRepository surveyRepository;
    private final SurveyQuestionRepository questionRepository;
    private final SurveyResponseRepository responseRepository;
    private final SurveyAnswerRepository answerRepository;
    private final SurveyTemplateService templateService;

    public SurveyService(SurveyRepository surveyRepository,
                         SurveyQuestionRepository questionRepository,
                         SurveyResponseRepository responseRepository,
                         SurveyAnswerRepository answerRepository,
                         SurveyTemplateService templateService) {
        this.surveyRepository = surveyRepository;
        this.questionRepository = questionRepository;
        this.responseRepository = responseRepository;
        this.answerRepository = answerRepository;
        this.templateService = templateService;
    }

    public List<Survey> findAll() {
        return surveyRepository.findAll();
    }

    public Page<Survey> findAll(Pageable pageable) {
        return surveyRepository.findAll(pageable);
    }

    public Survey findById(Long id) {
        return surveyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found: " + id));
    }

    public Survey save(Survey survey) {
        return surveyRepository.save(survey);
    }

    @Transactional
    public void delete(Long id) {
        Survey s = findById(id);
        // Delete answers for this survey's responses
        List<SurveyResponse> responses = responseRepository.findBySurveyId(id);
        for (SurveyResponse resp : responses) {
            answerRepository.findByResponseId(resp.getId())
                    .forEach(answerRepository::delete);
            responseRepository.delete(resp);
        }
        // Delete questions
        questionRepository.findBySurveyId(id)
                .forEach(questionRepository::delete);
        surveyRepository.delete(s);
    }

    public List<Survey> getVisibleForEmployee(User user) {
        LocalDate today = LocalDate.now();
        return surveyRepository.findByStatus(SurveyStatus.ACTIVE).stream()
                .filter(s -> !today.isBefore(s.getStartDate()) && !today.isAfter(s.getEndDate()))
                .filter(s -> isTargeted(s, user))
                .filter(s -> !hasAlreadyResponded(s, user))
                .toList();
    }

    private boolean isTargeted(Survey survey, User user) {
        return switch (survey.getTargetScope()) {
            case COMPANY -> true;
            case DEPARTMENT -> user.getDepartment() != null && user.getDepartment().equals(survey.getTargetDepartment());
            case TEAM -> user.getManager() != null && user.getManager().getId().equals(survey.getTargetManagerId());
            case INDIVIDUAL -> user.getId().equals(survey.getTargetUserId());
        };
    }

    private boolean hasAlreadyResponded(Survey survey, User user) {
        return !responseRepository.findBySurveyIdAndUserId(survey.getId(), user.getId()).isEmpty();
    }

    public List<SurveyQuestion> getQuestions(Long surveyId) {
        return questionRepository.findBySurveyIdOrderByOrderIndex(surveyId);
    }

    @Transactional
    public void saveTemplateQuestions(Survey survey, String templateKey) {
        List<SurveyQuestion> template = templateService.getTemplate(templateKey);
        for (SurveyQuestion q : template) {
            SurveyQuestion sq = SurveyQuestion.builder()
                    .survey(survey)
                    .text(q.getText())
                    .type(q.getType())
                    .orderIndex(q.getOrderIndex())
                    .build();
            questionRepository.save(sq);
        }
    }

    @Transactional
    public void saveCustomQuestions(Survey survey, List<String> texts, List<String> types) {
        if (texts == null || types == null) return;
        int size = Math.min(texts.size(), types.size());
        for (int i = 0; i < size; i++) {
            String text = texts.get(i);
            if (text == null || text.isBlank()) continue;
            SurveyQuestion sq = SurveyQuestion.builder()
                    .survey(survey)
                    .text(text.trim())
                    .type(QuestionType.valueOf(types.get(i)))
                    .orderIndex(i)
                    .build();
            questionRepository.save(sq);
        }
    }

    public void update(Survey survey, List<String> questionTexts, List<String> questionTypes) {
        surveyRepository.save(survey);
        // Remove old questions
        questionRepository.findBySurveyId(survey.getId())
                .forEach(questionRepository::delete);
        // Save new questions
        saveCustomQuestions(survey, questionTexts, questionTypes);
    }

    @Transactional
    public void submitResponse(Survey survey, User user, Map<Long, String> answers) {
        SurveyResponse response = new SurveyResponse();
        response.setSurvey(survey);
        response.setUserId(survey.isAnonymous() ? null : user.getId());
        response.setSubmittedAt(LocalDateTime.now());
        responseRepository.save(response);

        for (Map.Entry<Long, String> entry : answers.entrySet()) {
            SurveyQuestion question = questionRepository.findById(entry.getKey()).orElse(null);
            if (question == null) continue;
            SurveyAnswer answer = new SurveyAnswer();
            answer.setResponse(response);
            answer.setQuestion(question);
            if (question.getType() == QuestionType.LIKERT_5) {
                try { answer.setLikertValue(Integer.parseInt(entry.getValue())); } catch (Exception ignored) {}
            } else {
                answer.setTextValue(entry.getValue());
            }
            answerRepository.save(answer);
        }
    }

    @Transactional
    public void activate(Long id) {
        Survey s = findById(id);
        s.setStatus(SurveyStatus.ACTIVE);
        surveyRepository.save(s);
    }

    @Transactional
    public void close(Long id) {
        Survey s = findById(id);
        s.setStatus(SurveyStatus.CLOSED);
        surveyRepository.save(s);
    }

    // ── Analytics ──

    public long getResponseCount(Long surveyId) {
        return responseRepository.findBySurveyId(surveyId).size();
    }

    public SurveyAnalytics getAnalytics(Long surveyId) {
        List<SurveyResponse> responses = responseRepository.findBySurveyId(surveyId);
        List<SurveyQuestion> questions = getQuestions(surveyId);

        if (responses.isEmpty() || questions.isEmpty()) {
            return new SurveyAnalytics(surveyId, 0, List.of(), List.of());
        }

        // Batch-load ALL answers for all responses in one query
        List<Long> responseIds = responses.stream().map(SurveyResponse::getId).toList();
        List<SurveyAnswer> allAnswers = answerRepository.findByResponseIdIn(responseIds);

        // Index answers by (responseId, questionId) for O(1) lookup
        java.util.Map<String, List<SurveyAnswer>> answersByRespAndQ = allAnswers.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        a -> a.getResponse().getId() + ":" + a.getQuestion().getId()));

        // Per-question stats (only LIKERT)
        List<SurveyQuestionAnalytics> qAnalytics = new ArrayList<>();
        for (SurveyQuestion q : questions) {
            List<Integer> values = new ArrayList<>();
            for (SurveyResponse resp : responses) {
                String key = resp.getId() + ":" + q.getId();
                List<SurveyAnswer> matched = answersByRespAndQ.getOrDefault(key, List.of());
                for (SurveyAnswer a : matched) {
                    if (a.getLikertValue() != null) {
                        values.add(a.getLikertValue());
                    }
                }
            }
            if (!values.isEmpty()) {
                double avg = values.stream().mapToInt(i -> i).average().orElse(0);
                int min = values.stream().mapToInt(i -> i).min().orElse(0);
                int max = values.stream().mapToInt(i -> i).max().orElse(0);
                long count1 = values.stream().filter(v -> v == 1).count();
                long count2 = values.stream().filter(v -> v == 2).count();
                long count3 = values.stream().filter(v -> v == 3).count();
                long count4 = values.stream().filter(v -> v == 4).count();
                long count5 = values.stream().filter(v -> v == 5).count();
                qAnalytics.add(new SurveyQuestionAnalytics(q.getId(), q.getText(), values.size(), avg, min, max,
                        new long[]{count1, count2, count3, count4, count5}));
            }
        }

        // Free text answers
        List<SurveyFreeTextAnswer> freeTexts = new ArrayList<>();
        for (SurveyQuestion q : questions) {
            if (q.getType() != QuestionType.FREE_TEXT) continue;
            for (SurveyResponse resp : responses) {
                String key = resp.getId() + ":" + q.getId();
                List<SurveyAnswer> matched = answersByRespAndQ.getOrDefault(key, List.of());
                for (SurveyAnswer a : matched) {
                    if (a.getTextValue() != null && !a.getTextValue().isBlank()) {
                        freeTexts.add(new SurveyFreeTextAnswer(q.getText(), a.getTextValue(), resp.getSubmittedAt()));
                    }
                }
            }
        }

        return new SurveyAnalytics(surveyId, (int) responses.size(), qAnalytics, freeTexts);
    }

    // ── Inner DTOs for analytics ──

    public record SurveyAnalytics(long surveyId, int totalResponses,
                                  List<SurveyQuestionAnalytics> questions,
                                  List<SurveyFreeTextAnswer> freeTexts) {}

    public record SurveyQuestionAnalytics(long questionId, String questionText, int responseCount,
                                          double average, int min, int max, long[] distribution) {}

    public record SurveyFreeTextAnswer(String questionText, String answer, LocalDateTime submittedAt) {}
}
