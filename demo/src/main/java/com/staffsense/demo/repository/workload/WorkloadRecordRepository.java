package com.staffsense.demo.repository.workload;

import com.staffsense.demo.model.WorkloadRecord;
import com.staffsense.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkloadRecordRepository extends JpaRepository<WorkloadRecord, Long> {
    List<WorkloadRecord> findByUserIdOrderByPeriodEndDesc(Long userId);
}
