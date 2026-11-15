package com.staffsense.demo.repository;

import com.staffsense.demo.model.AdminAuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Журнал действий администратора. */
public interface AdminAuditRepository extends JpaRepository<AdminAuditEntry, Long> {

    Page<AdminAuditEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByAction(String action);
}
