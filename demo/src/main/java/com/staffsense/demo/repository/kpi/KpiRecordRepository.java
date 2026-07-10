package com.staffsense.demo.repository.kpi;

import com.staffsense.demo.model.KpiRecord;
import com.staffsense.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KpiRecordRepository extends JpaRepository<KpiRecord, Long> {
    List<KpiRecord> findByUserIdOrderByPeriodEndDesc(Long userId);
}
