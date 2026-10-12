package com.staffsense.demo.repository;

import com.staffsense.demo.model.MemoStatus;
import com.staffsense.demo.model.ServiceMemo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceMemoRepository extends JpaRepository<ServiceMemo, Long> {

    List<ServiceMemo> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ServiceMemo> findByUserIdInOrderByCreatedAtDesc(List<Long> userIds);

    List<ServiceMemo> findAllByOrderByCreatedAtDesc();

    List<ServiceMemo> findByStatusOrderByCreatedAtAsc(MemoStatus status);

    List<ServiceMemo> findByStatusInOrderByCreatedAtAsc(List<MemoStatus> statuses);

    long countByStatus(MemoStatus status);
}
