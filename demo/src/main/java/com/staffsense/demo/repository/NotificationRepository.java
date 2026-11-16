package com.staffsense.demo.repository;

import com.staffsense.demo.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Уведомления сотрудника. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);
}
