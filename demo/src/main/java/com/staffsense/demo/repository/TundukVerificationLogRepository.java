package com.staffsense.demo.repository;

import com.staffsense.demo.model.TundukVerificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TundukVerificationLogRepository extends JpaRepository<TundukVerificationLog, Long> {

    Page<TundukVerificationLog> findAllByOrderByRequestedAtDesc(Pageable pageable);

    List<TundukVerificationLog> findByInnOrderByRequestedAtDesc(String inn);
}
