package com.staffsense.demo.repository;

import com.staffsense.demo.model.Candidate;
import com.staffsense.demo.model.CandidateStage;
import com.staffsense.demo.model.Vacancy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Кандидаты вакансии. */
public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    List<Candidate> findByVacancyOrderByAppliedAtDesc(Vacancy vacancy);

    long countByVacancyAndStage(Vacancy vacancy, CandidateStage stage);

    List<Candidate> findByVacancyAndStageIn(Vacancy vacancy, List<CandidateStage> stages);
}
