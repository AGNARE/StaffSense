package com.staffsense.demo.repository;

import com.staffsense.demo.model.EmployeeDocuments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeDocumentsRepository extends JpaRepository<EmployeeDocuments, Long> {

    Optional<EmployeeDocuments> findByUserId(Long userId);

    Optional<EmployeeDocuments> findByInn(String inn);

    boolean existsByInn(String inn);

    List<EmployeeDocuments> findByTundukVerified(boolean verified);
}
