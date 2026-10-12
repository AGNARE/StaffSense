package com.staffsense.demo.repository;

import com.staffsense.demo.model.MeetingStatus;
import com.staffsense.demo.model.OneOnOneMeeting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OneOnOneMeetingRepository extends JpaRepository<OneOnOneMeeting, Long> {
    List<OneOnOneMeeting> findByEmployeeId(Long employeeId);

    Page<OneOnOneMeeting> findByEmployeeId(Long employeeId, Pageable pageable);

    Page<OneOnOneMeeting> findByManagerId(Long managerId, Pageable pageable);

    /**
     * Просроченные запланированные встречи — для автопометки missed (FR-026, BR-021).
     */
    List<OneOnOneMeeting> findByStatusAndScheduledAtBefore(MeetingStatus status, LocalDateTime threshold);
}
