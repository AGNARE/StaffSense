package com.staffsense.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Владелец проекта обязателен (FR-007). В миграции V1 колонка
     * {@code owner_pm_id} объявлена {@code NOT NULL}, поэтому до этой правки
     * создание проекта без владельца приводило к 409 от ограничения БД,
     * а не к сообщению у поля формы (ERR-006).
     */
    @NotNull(message = "Выберите владельца проекта (PM)")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_pm_id", nullable = false)
    private User ownerPm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
