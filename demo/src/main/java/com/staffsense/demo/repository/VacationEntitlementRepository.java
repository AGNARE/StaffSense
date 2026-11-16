package com.staffsense.demo.repository;

import com.staffsense.demo.model.VacationEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Право на отпуск по сотрудникам и годам. */
public interface VacationEntitlementRepository extends JpaRepository<VacationEntitlement, Long> {

    Optional<VacationEntitlement> findByUserIdAndYear(Long userId, Integer year);

    List<VacationEntitlement> findByYear(Integer year);
}
