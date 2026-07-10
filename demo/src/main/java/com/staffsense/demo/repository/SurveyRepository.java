package com.staffsense.demo.repository;

import com.staffsense.demo.model.Survey;
import com.staffsense.demo.model.SurveyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SurveyRepository extends JpaRepository<Survey, Long> {
    List<Survey> findByStatus(SurveyStatus status);
}
