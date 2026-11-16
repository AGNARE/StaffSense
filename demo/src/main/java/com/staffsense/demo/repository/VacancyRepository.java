package com.staffsense.demo.repository;

import com.staffsense.demo.model.Vacancy;
import com.staffsense.demo.model.VacancyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Вакансии. */
public interface VacancyRepository extends JpaRepository<Vacancy, Long> {

    List<Vacancy> findAllByOrderByCreatedAtDesc();

    List<Vacancy> findByStatusOrderByCreatedAtDesc(VacancyStatus status);
}
