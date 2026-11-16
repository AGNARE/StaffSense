package com.staffsense.demo.api.skill;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.AiSkill;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.SkillRole;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.AiSkillCatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Каталог ИИ-скиллов (FR-033, UC-016).
 *
 * <p>Содержимое файлов в JSON не попадает — только метаданные. Файл отдаётся
 * отдельной ручкой: иначе список из десятка скиллов тянул бы мегабайты
 * в каждом ответе.
 *
 * <p>Черновики каталога видят только ADMIN и HR — то же правило, что на
 * серверной странице управления.
 */
@RestController
@RequestMapping("/api/v1/skills")
@Tag(name = "ИИ-скиллы", description = "Каталог скиллов и скачивание файлов")
public class SkillApiController {

    private final AiSkillCatalogService catalogService;
    private final UserRepository userRepository;

    public SkillApiController(AiSkillCatalogService catalogService,
                              UserRepository userRepository) {
        this.catalogService = catalogService;
        this.userRepository = userRepository;
    }

    /** Каталог: опубликованные скиллы, при желании — по роли. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<SkillDto> list(@RequestParam(name = "role", required = false) String role,
                               Authentication auth) {
        User actor = currentUser(auth);
        boolean manages = actor.getRole() == Role.ADMIN || actor.getRole() == Role.HR;

        SkillRole filter = role == null || role.isBlank() ? null : parseRole(role);
        List<AiSkill> skills = manages && filter == null
                ? catalogService.allForManage()
                : catalogService.catalog(filter);

        return skills.stream().map(this::toDto).toList();
    }

    /** Метаданные одного скилла. */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public SkillDto one(@PathVariable Long id, Authentication auth) {
        User actor = currentUser(auth);
        AiSkill skill = catalogService.get(id);
        if (!skill.isPublished() && !manages(actor)) {
            throw new ResourceNotFoundException("Скилл не найден: " + id);
        }
        return toDto(skill);
    }

    /** Скачивание файла скилла. */
    @GetMapping("/{id}/file")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> file(@PathVariable Long id, Authentication auth) {
        User actor = currentUser(auth);
        AiSkill skill = catalogService.get(id);
        if (!skill.isPublished() && !manages(actor)) {
            throw new ResourceNotFoundException("Скилл не найден: " + id);
        }

        AiSkillCatalogService.SkillFile file = catalogService.file(id);
        return ResponseEntity.ok()
                .contentType(parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.name() + "\"")
                .body(new ByteArrayResource(file.data()));
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private User currentUser(Authentication auth) {
        if (auth == null) {
            throw new ResourceNotFoundException("Пользователь не определён");
        }
        return userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new ResourceNotFoundException("Пользователь не найден: " + auth.getName()));
    }

    private boolean manages(User actor) {
        return actor.getRole() == Role.ADMIN || actor.getRole() == Role.HR;
    }

    private SkillRole parseRole(String raw) {
        try {
            return SkillRole.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Неизвестная роль: " + raw);
        }
    }

    private MediaType parseMediaType(String raw) {
        try {
            return raw == null || raw.isBlank() ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(raw);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private SkillDto toDto(AiSkill skill) {
        return new SkillDto(
                skill.getId(),
                skill.getName(),
                skill.getDescription(),
                skill.getTargetRole().name(),
                skill.getTargetRole().getTitle(),
                skill.getFileName(),
                skill.getGithubUrl(),
                skill.isPublished(),
                skill.getUpdatedAt());
    }

    /** Метаданные скилла: без содержимого файла. */
    public record SkillDto(
            Long id,
            String name,
            String description,
            String targetRole,
            String targetRoleTitle,
            String fileName,
            String githubUrl,
            boolean published,
            LocalDateTime updatedAt
    ) {
    }
}
