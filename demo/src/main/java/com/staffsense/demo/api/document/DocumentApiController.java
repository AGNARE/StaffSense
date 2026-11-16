package com.staffsense.demo.api.document;

import com.staffsense.demo.exception.DocumentTransitionException;
import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.DocumentStatus;
import com.staffsense.demo.model.DocumentType;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.DocumentService;
import com.staffsense.demo.service.document.DocumentAccessPolicy;
import com.staffsense.demo.service.document.DocumentRouteService;
import com.staffsense.demo.service.document.DocumentSignatureService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Реестр документов и маршрут согласования.
 *
 * <p>Чтение доступно ADMIN, HR, PM и EXEC; регистрация — только ADMIN и HR.
 * Смену статуса выполняют ADMIN, HR и PM (последний — лишь по документам
 * своей команды). EMPLOYEE не видит реестр: там кадровые приказы и договоры
 * коллег. Права уровня данных проверяет
 * {@link com.staffsense.demo.service.document.DocumentAccessPolicy}.
 */
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Документы", description = "Реестр документов: приказы, договоры, справки")
public class DocumentApiController {

    private final DocumentService documentService;
    private final DocumentRouteService routeService;
    private final DocumentAccessPolicy accessPolicy;
    private final DocumentSignatureService signatureService;
    private final DocumentCardAssembler cardAssembler;
    private final UserRepository userRepository;

    public DocumentApiController(DocumentService documentService,
                                 DocumentRouteService routeService,
                                 DocumentAccessPolicy accessPolicy,
                                 DocumentSignatureService signatureService,
                                 DocumentCardAssembler cardAssembler,
                                 UserRepository userRepository) {
        this.documentService = documentService;
        this.routeService = routeService;
        this.accessPolicy = accessPolicy;
        this.signatureService = signatureService;
        this.cardAssembler = cardAssembler;
        this.userRepository = userRepository;
    }

    /**
     * Реестр с фильтрами по типу и статусу.
     *
     * <p>PM видит только документы своей команды: список — такая же точка
     * утечки, как и карточка, поэтому правило применяется и здесь.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR','PM','EXEC')")
    public List<DocumentDto> list(@RequestParam(name = "type", required = false) String type,
                                  @RequestParam(name = "status", required = false) String status,
                                  Authentication auth) {

        List<Document> documents;
        if (type != null && !type.isBlank()) {
            documents = documentService.byType(parseType(type));
        } else if (status != null && !status.isBlank()) {
            documents = documentService.byStatus(parseStatus(status));
        } else {
            documents = documentService.all();
        }

        User actor = currentUser(auth);
        return accessPolicy.filterVisible(actor, documents).stream().map(DocumentDto::from).toList();
    }

    /**
     * Входящие: документы, ожидающие решения текущего пользователя.
     *
     * <p>Для HR и ADMIN — «на согласовании»; PM — те же статусы, но только
     * по своей команде. Экран «Входящие и маршруты» строится отсюда.
     */
    @GetMapping("/inbox")
    @PreAuthorize("hasAnyRole('ADMIN','HR','PM','EXEC')")
    public List<DocumentDto> inbox(Authentication auth) {
        User actor = currentUser(auth);
        return routeService.inbox(actor).stream().map(DocumentDto::from).toList();
    }

    /**
     * Карточка документа: полные данные, история статусов и доступные
     * действия. PM откроет только документ своей команды, иначе — 403.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR','PM','EXEC')")
    public DocumentCardDto one(@PathVariable Long id, Authentication auth) {
        User actor = currentUser(auth);
        Document document = routeService.requireVisible(id, actor);
        return toCard(document, actor);
    }

    /** Регистрация документа: номер и срок хранения считает сервер. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public DocumentDto create(@Valid @RequestBody CreateRequest request, Authentication auth) {
        User author = currentUser(auth);
        Document document = documentService.register(
                request.type(), request.title(), request.summary(),
                request.employeeId(), request.documentDate(), author);
        // Первая запись таймлайна: без неё карточка нового документа пуста.
        routeService.recordCreated(document, author);
        return DocumentDto.from(document);
    }

    /**
     * Смена статуса по маршруту «черновик → согласование → утверждён → архив».
     *
     * <p>Каждый переход пишется в историю вместе с комментарием: карточка
     * должна отвечать, кто и почему сдвинул документ, а не только где он
     * сейчас. Недопустимый маршрутом переход возвращается как 409.
     */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','HR','PM')")
    public DocumentCardDto changeStatus(@PathVariable Long id,
                                        @Valid @RequestBody StatusRequest request,
                                        Authentication auth) {
        User actor = currentUser(auth);
        Document document = routeService.changeStatus(
                actor, id, parseStatus(request.status()), request.comment());
        // Переход мог поставить документ на согласование: срок подписи (SLA)
        // считается здесь же, иначе документ ждал бы подписи без дедлайна.
        document = signatureService.applyDeadline(document);
        return toCard(document, actor);
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    /**
     * Карточка после чтения/перехода: история, подписи и действия считаются в
     * {@link DocumentCardAssembler}, чтобы подпись и реестр отдавали одинаковый
     * контракт.
     */
    private DocumentCardDto toCard(Document document, User actor) {
        return cardAssembler.of(document, actor);
    }

    /**
     * Недопустимый переход статуса документа — 409, а не 500.
     *
     * <p>Обработчик локальный: глобальный {@code GlobalControllerAdvice}
     * намеренно не трогаем, а для остальных эндпоинтов его поведение
     * остаётся прежним.
     */
    @ExceptionHandler(DocumentTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleTransition(DocumentTransitionException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", HttpStatus.CONFLICT.getReasonPhrase());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    private DocumentType parseType(String raw) {
        try {
            return DocumentType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Неизвестный тип документа: " + raw);
        }
    }

    private DocumentStatus parseStatus(String raw) {
        try {
            return DocumentStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResourceNotFoundException("Неизвестный статус: " + raw);
        }
    }

    private User currentUser(Authentication auth) {
        if (auth == null) {
            throw new ResourceNotFoundException("Пользователь не определён");
        }
        return userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new ResourceNotFoundException("Пользователь не найден: " + auth.getName()));
    }

    /** Тело запроса на регистрацию. */
    public record CreateRequest(
            @NotNull(message = "Тип документа обязателен") DocumentType type,
            @NotBlank(message = "Название обязательно")
            @Size(max = 300, message = "Название слишком длинное") String title,
            @Size(max = 500, message = "Описание слишком длинное") String summary,
            Long employeeId,
            LocalDate documentDate
    ) {
    }

    /** Тело запроса на смену статуса. */
    public record StatusRequest(
            String status,
            @Size(max = 500, message = "Комментарий слишком длинный") String comment
    ) {
    }

    /** Строка реестра. */
    public record DocumentDto(
            Long id,
            String regNumber,
            String type,
            String typeTitle,
            String title,
            String summary,
            Long employeeId,
            String employeeName,
            String authorName,
            LocalDate documentDate,
            LocalDate retentionUntil,
            boolean permanentRetention,
            String status,
            String statusTitle
    ) {
        public static DocumentDto from(Document document) {
            return new DocumentDto(
                    document.getId(),
                    document.getRegNumber(),
                    document.getDocType().name(),
                    document.getDocType().getTitle(),
                    document.getTitle(),
                    document.getSummary(),
                    document.getEmployee() != null ? document.getEmployee().getId() : null,
                    document.getEmployee() != null ? document.getEmployee().getFullName() : null,
                    document.getAuthor() != null ? document.getAuthor().getFullName() : null,
                    document.getDocumentDate(),
                    document.getRetentionUntil(),
                    document.getDocType().isPermanent(),
                    document.getStatus().name(),
                    document.getStatus().getTitle());
        }
    }
}
