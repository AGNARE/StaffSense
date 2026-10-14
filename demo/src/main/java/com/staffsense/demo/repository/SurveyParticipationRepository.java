package com.staffsense.demo.repository;

import com.staffsense.demo.model.SurveyParticipation;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Учёт участия в опросах (FR-022, ERR-012).
 */
public interface SurveyParticipationRepository extends JpaRepository<SurveyParticipation, Long> {

    /** Проходил ли сотрудник этот опрос — работает и для анонимных опросов. */
    boolean existsBySurveyIdAndUserId(Long surveyId, Long userId);
}
