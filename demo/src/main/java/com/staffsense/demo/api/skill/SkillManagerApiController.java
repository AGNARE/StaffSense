package com.staffsense.demo.api.skill;

import com.staffsense.demo.model.AiSkill;
import com.staffsense.demo.model.SkillRole;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.AiSkillCatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Управление каталогом ИИ-скиллов (FR-033, UC-016): создание, правка,
 * публикация/снятие с публикации, удаление.
 *
 * <p><b>Почему отдельный контроллер.</b> {@link SkillApiController} — чтение
 * каталога, доступное всем авторизованным. Мутации требуют ADMIN или HR и
 * живут здесь, чтобы правило «кто наполняет каталог» читалось по имени класса,
 * а не по аннотациям внутри read-only контракта.
 *
 * <p><b>Формат запроса — {@code multipart/form-data}, не JSON + base64.</b>
 * Причины: (1) сервис {@link AiSkillCatalogService} уже принимает
 * {@code MultipartFile} и умеет «файл не загружен — прежний сохраняется»,
 * конвертировать нечего; (2) base64 раздувает тело на треть и требует
 * декодирования на сервере ради того же результата; (3) браузер собирает
 * такое тело сам — {@code FormData}, без чтения файла в память скриптом.
 * CSRF при этом работает штатно: токен уходит заголовком
 * {@code X-XSRF-TOKEN} из {@code GET /api/v1/csrf} (старый дефект с 403
 * касался формы Thymeleaf, где токен передавался полем multipart-тела).
 *
 * <p><b>Текстовые поля — параметры формы, а не {@code @ModelAttribute}.</b>
 * Валидация через {@code @Valid} на модели превращает ошибку в
 * {@code BindException}, для которой в общем совете нет обработчика (было бы
 * 500). Здесь проверки явные и бросают {@link IllegalArgumentException} —
 * общий совет отдаёт на него 400 с текстом.
 *
 * <p><b>Новые методы сервиса не добавлялись:</b> файл каталога — чужая зона,
 * поэтому публикация идёт через {@code update} с копией текущих полей.
 */
@RestController
@RequestMapping("/api/v1/skills")
@Tag(name = "ИИ-скиллы: управление",
        description = "Создание, правка, публикация и удаление скиллов (ADMIN, HR)")
public class SkillManagerApiController {

    /** Ограничения повторяют {@link AiSkill}: длиннее — 400, а не 500 из Hibernate. */
    private static final int MAX_NAME = 150;
    private static final int MAX_URL = 500;

    private final AiSkillCatalogService catalogService;
    private final UserRepository userRepository;

    public SkillManagerApiController(AiSkillCatalogService catalogService,
                                     UserRepository userRepository) {
        this.catalogService = catalogService;
        this.userRepository = userRepository;
    }

    /**
     * Роли каталога для формы создания и правки.
     *
     * <p>Отдельная ручка, чтобы клиент не дублировал {@link SkillRole} и русские
     * названия ролей: это доменный справочник, а не оформление экрана.
     */
    @GetMapping("/roles")
    @PreAuthorize("isAuthenticated()")
    public List<SkillManagerApiDtos.RoleOption> roles() {
        return SkillManagerApiDtos.roles();
    }

    /**
     * Создание скилла.
     *
     * @param name        название, обязательно (до 150 символов)
     * @param description что делает скилл и когда применяется
     * @param targetRole  роль из {@link SkillRole}, обязательна
     * @param githubUrl   ссылка на репозиторий, необязательна
     * @param published   публиковать сразу; не передан — {@code true} (как в форме)
     * @param file        файл скилла, необязателен
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @ResponseStatus(HttpStatus.CREATED)
    public SkillApiController.SkillDto create(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "targetRole", required = false) String targetRole,
            @RequestParam(name = "githubUrl", required = false) String githubUrl,
            @RequestParam(name = "published", required = false) Boolean published,
            @RequestParam(name = "file", required = false) MultipartFile file,
            Authentication auth) {

        AiSkill form = new AiSkill();
        form.setName(requireName(name));
        form.setDescription(blankToNull(description));
        form.setTargetRole(requireRole(targetRole));
        form.setGithubUrl(requireUrl(githubUrl));
        form.setPublished(published == null || published);

        return SkillManagerApiDtos.skill(catalogService.create(form, file, requireActor(auth)));
    }

    /**
     * Правка скилла. Любое поле можно опустить — тогда оно не меняется;
     * загруженный файл заменяет прежний, отсутствие файла его сохраняет.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public SkillApiController.SkillDto update(
            @PathVariable Long id,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "targetRole", required = false) String targetRole,
            @RequestParam(name = "githubUrl", required = false) String githubUrl,
            @RequestParam(name = "published", required = false) Boolean published,
            @RequestParam(name = "file", required = false) MultipartFile file,
            Authentication auth) {

        // 404 до разбора полей: правка несуществующего скилла — не ошибка ввода.
        AiSkill existing = catalogService.get(id);

        AiSkill form = new AiSkill();
        form.setName(name == null ? existing.getName() : requireName(name));
        form.setDescription(description == null
                ? existing.getDescription() : blankToNull(description));
        form.setTargetRole(targetRole == null
                ? existing.getTargetRole() : requireRole(targetRole));
        form.setGithubUrl(githubUrl == null ? existing.getGithubUrl() : requireUrl(githubUrl));
        form.setPublished(published == null ? existing.isPublished() : published);

        return SkillManagerApiDtos.skill(catalogService.update(id, form, file, requireActor(auth)));
    }

    /**
     * Публикация и снятие с публикации одной ручкой.
     *
     * <p>{@code POST /api/v1/skills/7/publish} — опубликовать (значение по
     * умолчанию), {@code ...?published=false} — вернуть в черновики. Одно
     * действие и его отмена — не два разных состояния модели, а флаг.
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public SkillApiController.SkillDto publish(
            @PathVariable Long id,
            @RequestParam(name = "published", required = false, defaultValue = "true") boolean published,
            Authentication auth) {

        AiSkill existing = catalogService.get(id);

        // Идём через update, а не через репозиторий: единственная точка записи
        // скилла — сервис каталога, и он же обновляет updatedAt.
        AiSkill form = new AiSkill();
        form.setName(existing.getName());
        form.setDescription(existing.getDescription());
        form.setTargetRole(existing.getTargetRole());
        form.setGithubUrl(existing.getGithubUrl());
        form.setPublished(published);

        return SkillManagerApiDtos.skill(catalogService.update(id, form, null, requireActor(auth)));
    }

    /** Удаление скилла. 204 — тело не нужно: клиент и так перечитает каталог. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        catalogService.delete(id);
    }

    // ── ошибки формата ───────────────────────────────────────────────────

    /**
     * Файл больше лимита контейнера.
     *
     * <p>Без этого обработчика {@code MaxUploadSizeExceededException} попадает
     * в общий {@code Exception} → 500 и текст «внутренняя ошибка», хотя клиент
     * прислал слишком большой файл. Лимит задаётся
     * {@code spring.servlet.multipart.max-file-size} (по умолчанию 1 МБ).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException ex,
                                                              HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", HttpStatus.PAYLOAD_TOO_LARGE.value());
        body.put("error", HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase());
        body.put("message", "Файл скилла больше допустимого размера "
                + "(лимит spring.servlet.multipart.max-file-size, по умолчанию 1 МБ)");
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private User requireActor(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new IllegalArgumentException("Пользователь не определён");
        }
        return userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new IllegalArgumentException("Пользователь не найден: " + auth.getName()));
    }

    private static String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Укажите название скилла");
        }
        if (name.length() > MAX_NAME) {
            throw new IllegalArgumentException(
                    "Название длиннее " + MAX_NAME + " символов — сократите");
        }
        return name;
    }

    private static SkillRole requireRole(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Выберите роль, для которой предназначен скилл");
        }
        try {
            return SkillRole.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Неизвестная роль: " + raw);
        }
    }

    private static String requireUrl(String raw) {
        String url = blankToNull(raw);
        if (url == null) {
            return null;
        }
        if (url.length() > MAX_URL) {
            throw new IllegalArgumentException(
                    "Ссылка длиннее " + MAX_URL + " символов — сократите");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("Ссылка на репозиторий должна начинаться с http(s)://");
        }
        return url;
    }

    private static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }
}
