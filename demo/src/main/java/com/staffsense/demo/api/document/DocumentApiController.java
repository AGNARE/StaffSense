package com.staffsense.demo.api.document;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.DocumentStatus;
import com.staffsense.demo.model.DocumentType;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.DocumentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Реестр документов.
 *
 * <p>Чтение доступно всем авторизованным, регистрация и смена статуса —
 * только ADMIN и HR: документы ведёт кадровая служба.
 */
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Документы", description = "Реестр документов: приказы, договоры, справки")
public class DocumentApiController {

    private final DocumentService documentService;
    private final UserRepository userRepository;

    public DocumentApiController(DocumentService documentService, UserRepository userRepository) {
        this.documentService = documentService;
        this.userRepository = userRepository;
    }

    /** Реестр с фильтрами по типу и статусу. Доступен тем, у кого есть раздел в меню. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR','PM','EXEC')")
    public List<DocumentDto> list(@RequestParam(name = "type", required = false) String type,
                                  @RequestParam(name = "status", required = false) String status) {

        List<Document> documents;
        if (type != null && !type.isBlank()) {
            documents = documentService.byType(parseType(type));
        } else if (status != null && !status.isBlank()) {
            documents = documentService.byStatus(parseStatus(status));
        } else {
            documents = documentService.all();
        }
        return documents.stream().map(DocumentDto::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR','PM','EXEC')")
    public DocumentDto one(@PathVariable Long id) {
        return DocumentDto.from(documentService.get(id));
    }

    /** Регистрация документа: номер и срок хранения считает сервер. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public DocumentDto create(@Valid @RequestBody CreateRequest request, Authentication auth) {
        User author = currentUser(auth);
        Document document = documentService.register(
                request.type(), request.title(), request.summary(),
                request.employeeId(), request.documentDate(), author);
        return DocumentDto.from(document);
    }

    /** Смена статуса по маршруту «черновик → согласование → утверждён → архив». */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public DocumentDto changeStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        return DocumentDto.from(documentService.changeStatus(id, parseStatus(request.status())));
    }

    // ── вспомогательное ──────────────────────────────────────────────────

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
    public record StatusRequest(String status) {
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
