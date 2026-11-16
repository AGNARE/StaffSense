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
 * Запись истории маршрута документа.
 *
 * <p>Одна строка — один переход статуса: от какого состояния к какому, кто
 * его выполнил, когда и с каким комментарием. Запись о регистрации имеет
 * {@code fromStatus = null}: предыдущего состояния у нового документа нет.
 *
 * <p>Строки только добавляются: история не переписывается при последующих
 * переходах, иначе вопрос «кто согласовал» перестал бы иметь ответ.
 */
@Entity
@Table(name = "document_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /** Предыдущий статус; null — документ только что зарегистрирован. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private DocumentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private DocumentStatus toStatus;

    /** Кто выполнил переход; null — действие системы (например, бэкфилл). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
