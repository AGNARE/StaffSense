package com.staffsense.demo.repository;

import com.staffsense.demo.model.DocumentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** История переходов статусов документов. */
@Repository
public interface DocumentStatusHistoryRepository extends JpaRepository<DocumentStatusHistory, Long> {

    /** История одного документа от ранних записей к поздним. */
    List<DocumentStatusHistory> findByDocumentIdOrderByCreatedAtAscIdAsc(Long documentId);

    long countByDocumentId(Long documentId);
}
