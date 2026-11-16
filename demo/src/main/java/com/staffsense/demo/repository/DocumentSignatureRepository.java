package com.staffsense.demo.repository;

import com.staffsense.demo.model.DocumentSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Подписи документов: реестр решений, а не изменяемое поле документа. */
@Repository
public interface DocumentSignatureRepository extends JpaRepository<DocumentSignature, Long> {

    /** Подписи одного документа от ранних к поздним — так их показывает карточка. */
    List<DocumentSignature> findByDocumentIdOrderBySignedAtAscIdAsc(Long documentId);

    /**
     * Уже подписывал ли этот сотрудник этот документ.
     *
     * <p>Проверка повторной подписи: без неё двойной клик по кнопке превращался
     * бы в две записи реестра.
     */
    boolean existsByDocumentIdAndSignerId(Long documentId, Long signerId);

    long countByDocumentId(Long documentId);
}
