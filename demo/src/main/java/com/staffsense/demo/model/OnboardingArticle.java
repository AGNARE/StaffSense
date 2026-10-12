package com.staffsense.demo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Статья внутреннего портала онбординга (модуль B).
 *
 * <p>Содержимое редактируется HR и ADMIN, читается всеми сотрудниками.
 */
@Entity
@Table(name = "onboarding_article")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardingArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 30)
    private OnboardingSection section;

    @NotBlank
    @Size(max = 200)
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @NotNull
    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    /** Черновик не виден сотрудникам (виден только HR/ADMIN). */
    @Column(name = "published", nullable = false)
    @Builder.Default
    private boolean published = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private User updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        this.updatedAt = LocalDateTime.now();
        if (this.orderIndex == null) {
            this.orderIndex = 0;
        }
    }

    /** Короткий анонс для списка статей. */
    @Transient
    public String getSummary() {
        if (content == null || content.isBlank()) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= 160 ? flat : flat.substring(0, 157) + "…";
    }
}
