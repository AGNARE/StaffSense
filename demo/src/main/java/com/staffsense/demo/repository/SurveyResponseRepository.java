package com.staffsense.demo.repository;

import com.staffsense.demo.model.SurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {
    List<SurveyResponse> findBySurveyId(Long surveyId);
    List<SurveyResponse> findByUserId(Long userId);
    List<SurveyResponse> findByUserIdAndSubmittedAtAfter(Long userId, java.time.LocalDateTime cutoff);
    List<SurveyResponse> findBySurveyIdAndUserId(Long surveyId, Long userId);
}
