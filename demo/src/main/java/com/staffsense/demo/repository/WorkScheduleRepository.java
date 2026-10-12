package com.staffsense.demo.repository;

import com.staffsense.demo.model.WorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {

    List<WorkSchedule> findByUserIdAndWorkDateBetweenOrderByWorkDateAsc(Long userId,
                                                                        LocalDate from,
                                                                        LocalDate to);

    List<WorkSchedule> findByUserIdOrderByWorkDateDesc(Long userId);

    Optional<WorkSchedule> findByUserIdAndWorkDate(Long userId, LocalDate workDate);

    List<WorkSchedule> findByUserIdInAndWorkDateBetweenOrderByWorkDateAsc(List<Long> userIds,
                                                                          LocalDate from,
                                                                          LocalDate to);

    void deleteByUserIdAndWorkDateBetween(Long userId, LocalDate from, LocalDate to);
}
