package com.staffsense.demo.repository.workload;

import com.staffsense.demo.model.WorkloadRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkloadRecordRepository extends JpaRepository<WorkloadRecord, Long> {

    List<WorkloadRecord> findByUserIdOrderByPeriodEndDesc(Long userId);

    /** Запись за конкретный период — нужна для upsert (uq_workload_records_user_period). */
    Optional<WorkloadRecord> findFirstByUserIdAndPeriodStartAndPeriodEnd(Long userId,
                                                                        LocalDate periodStart,
                                                                        LocalDate periodEnd);
}
