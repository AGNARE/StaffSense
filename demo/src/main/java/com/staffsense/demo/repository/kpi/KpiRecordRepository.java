package com.staffsense.demo.repository.kpi;

import com.staffsense.demo.model.KpiRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface KpiRecordRepository extends JpaRepository<KpiRecord, Long> {

    List<KpiRecord> findByUserIdOrderByPeriodEndDesc(Long userId);

    /**
     * Запись за конкретный период — нужна для upsert.
     * Без неё повторный пересчёт нарушал бы уникальность
     * uq_kpi_records_user_period.
     */
    Optional<KpiRecord> findFirstByUserIdAndPeriodStartAndPeriodEnd(Long userId,
                                                                    LocalDate periodStart,
                                                                    LocalDate periodEnd);
}
