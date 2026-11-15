package com.staffsense.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Запись журнала действий администратора.
 *
 * <p>Актор и цель хранятся снимком (id + email на момент действия): учётную
 * запись могут переименовать или удалить, а вопрос «кто и что сделал» должен
 * оставаться отвечаемым.
 */
@Entity
@Table(name = "admin_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actorId;

    @Column(length = 150)
    private String actorEmail;

    @Column(nullable = false, length = 40)
    private String action;

    private Long targetId;

    @Column(length = 150)
    private String targetEmail;

    @Column(length = 500)
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
