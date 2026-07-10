package com.staffsense.demo.repository;

import com.staffsense.demo.model.SurveyAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, Long> {
    List<SurveyAnswer> findByResponseId(Long responseId);
    List<SurveyAnswer> findByQuestionId(Long questionId);
    List<SurveyAnswer> findByResponseSurveyId(Long surveyId);
    List<SurveyAnswer> findByResponseIdAndLikertValueIsNotNull(Long responseId);
    List<SurveyAnswer> findByResponseIdAndQuestionId(Long responseId, Long questionId);
    List<SurveyAnswer> findByResponseIdIn(List<Long> responseIds);
}
