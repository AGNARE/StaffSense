package com.staffsense.demo.repository;

import com.staffsense.demo.model.WorkSchedulePeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkSchedulePeriodRepository extends JpaRepository<WorkSchedulePeriod, Long> {

    Optional<WorkSchedulePeriod> findByManagerIdAndPeriodStartAndPeriodEnd(Long managerId,
                                                                          LocalDate periodStart,
                                                                          LocalDate periodEnd);

    List<WorkSchedulePeriod> findByManagerIdOrderByPeriodStartDesc(Long managerId);

    List<WorkSchedulePeriod> findAllByOrderByPeriodStartDesc();
}
