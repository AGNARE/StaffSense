package com.staffsense.demo.repository;

import com.staffsense.demo.model.SurveyQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, Long> {
    List<SurveyQuestion> findBySurveyIdOrderByOrderIndex(Long surveyId);
    List<SurveyQuestion> findBySurveyId(Long surveyId);
    void deleteBySurveyId(Long surveyId);
}
