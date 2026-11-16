package com.staffsense.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Подпись документа: кто, когда, какое решение и с каким комментарием.
 *
 * <p>Строки только добавляются: подпись — это факт, а не редактируемое поле.
 * Повторная подпись тем же человеком запрещена (уникальный индекс +
 * проверка в {@code DocumentSignatureService}) — иначе «кто согласовал»
 * снова перестал бы иметь однозначный ответ.
 */
@Entity
@Table(name = "document_signature")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /** Кто подписал: ссылка на сотрудника, а не строка с именем. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "signer_id", nullable = false)
    private User signer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SignatureDecision decision;

    /** Почему так решили; обязательным не является (кроме отклонения по правилам). */
    @Column(length = 500)
    private String comment;

    /** Момент решения: по нему считается, успели ли в SLA. */
    @Column(name = "signed_at", nullable = false)
    private LocalDateTime signedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
