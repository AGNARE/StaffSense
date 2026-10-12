package com.staffsense.demo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Журнал обращений к «Тундук» (BR-026).
 *
 * <p>Требование раздела 26 ТЗ: обработка персональных данных должна быть
 * прослеживаемой — кто, когда и по какому ИНН запрашивал данные.
 */
@Entity
@Table(name = "tunduk_verification_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TundukVerificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 14)
    @Column(name = "inn", nullable = false, length = 14)
    private String inn;

    /** Кто выполнил запрос (HR/ADMIN). */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private User requestedBy;

    @NotNull
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private TundukVerificationResult result;

    /** Сотрудник, к которому относится запрос (если он есть в системе). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @PrePersist
    protected void onCreate() {
        if (this.requestedAt == null) {
            this.requestedAt = LocalDateTime.now();
        }
    }
}
