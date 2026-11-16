package com.staffsense.demo.repository;

import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.DocumentStatus;
import com.staffsense.demo.model.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** Реестр документов. */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findAllByOrderByDocumentDateDesc();

    List<Document> findByDocTypeOrderByDocumentDateDesc(DocumentType docType);

    List<Document> findByStatusOrderByDocumentDateDesc(DocumentStatus status);

    List<Document> findByEmployeeIdOrderByDocumentDateDesc(Long employeeId);

    /**
     * Документы в заданном статусе, у которых срок подписи уже прошёл.
     *
     * <p>Нужен планировщику SLA и очереди на подпись: выборка «просрочено»
     * должна считаться в БД, а не перебором всего реестра. Документы без
     * {@code due_at} в выборку не попадают — у них срока нет.
     */
    List<Document> findByStatusAndDueAtBeforeOrderByDueAtAsc(DocumentStatus status, LocalDateTime moment);

    /** Сколько документов этого типа уже зарегистрировано — для номера. */
    long countByDocType(DocumentType docType);

    boolean existsByRegNumber(String regNumber);
}
