package com.staffsense.demo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ИИ-скилл в каталоге (эпик E-08, FR-033, UC-016).
 *
 * <p>Каталог — отдельная вкладка: сотрудник видит скиллы с названием, описанием
 * и ролью, для которой скилл предназначен, может скачать файл и перейти по ссылке
 * в репозиторий GitHub, если скилл там опубликован.
 *
 * <p>Вызов скилла и рекомендации ИИ в объём не входят — по решению заказчика эпик
 * ограничен каталогом.
 *
 * <p>Файл скилла хранится в базе: так скачивание не зависит от файловой системы
 * и не ломается при переносе приложения.
 */
@Entity
@Table(name = "ai_skill")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Укажите название скилла")
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** Роль, для которой предназначен скилл. */
    @NotNull(message = "Выберите роль")
    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", nullable = false, length = 30)
    private SkillRole targetRole;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "file_data")
    private byte[] fileData;

    /** Ссылка на репозиторий GitHub; может отсутствовать. */
    @Size(max = 500)
    @Column(name = "github_url", length = 500)
    private String githubUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean published = true;

    @Column(name = "created_by_id")
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Есть ли файл для скачивания. */
    @Transient
    public boolean hasFile() {
        return fileData != null && fileData.length > 0;
    }

    /** Есть ли ссылка на репозиторий. */
    @Transient
    public boolean hasRepository() {
        return githubUrl != null && !githubUrl.isBlank();
    }
}
