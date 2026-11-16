package com.staffsense.demo.service;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.DocumentStatus;
import com.staffsense.demo.model.DocumentType;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.DocumentRepository;
import com.staffsense.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Реестр документов: регистрация, срок хранения, смена статуса.
 *
 * <p>Правила:
 * <ul>
 *   <li>номер формируется как {@code <порядковый>-<префикс типа>/<год>} и
 *       уникален (в БД стоит уникальный индекс);</li>
 *   <li>срок хранения берётся из типа документа; для договора он не
 *       ограничен (null), для приказа и справки — дата + срок в годах;</li>
 *   <li>переходы статусов описаны в {@link DocumentStatus}; повторная
 *       установка того же статуса не считается ошибкой.</li>
 * </ul>
 *
 * <p>Известное ограничение: номер считается как «количество + 1». При
 * одновременной регистрации двух документов возможна коллизия — её поймает
 * уникальный индекс, но пользователь получит ошибку. Для одиночной работы
 * отдела кадров этого достаточно; честная последовательность потребует
 * отдельной таблицы счётчиков.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    public DocumentService(DocumentRepository documentRepository, UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

    public List<Document> all() {
        return documentRepository.findAllByOrderByDocumentDateDesc();
    }

    public List<Document> byType(DocumentType type) {
        return documentRepository.findByDocTypeOrderByDocumentDateDesc(type);
    }

    public List<Document> byStatus(DocumentStatus status) {
        return documentRepository.findByStatusOrderByDocumentDateDesc(status);
    }

    public Document get(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Документ не найден: " + id));
    }

    /** Регистрация документа: номер, срок хранения, статус «черновик». */
    @Transactional
    public Document register(DocumentType type, String title, String summary,
                             Long employeeId, LocalDate documentDate, User author) {

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Название документа обязательно");
        }

        LocalDate date = documentDate != null ? documentDate : LocalDate.now();
        String regNumber = nextRegNumber(type, date);

        User employee = employeeId == null ? null : userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Сотрудник не найден: " + employeeId));

        Document document = Document.builder()
                .regNumber(regNumber)
                .docType(type)
                .title(title.trim())
                .summary(summary)
                .employee(employee)
                .author(author)
                .documentDate(date)
                .retentionUntil(retentionUntil(type, date))
                .status(DocumentStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .build();

        Document saved = documentRepository.save(document);
        log.info("Документ зарегистрирован: {} ({})", regNumber, type);
        return saved;
    }

    /** Смена статуса с проверкой маршрута. */
    @Transactional
    public Document changeStatus(Long id, DocumentStatus target) {
        Document document = get(id);

        if (!document.getStatus().canTransitionTo(target)) {
            throw new IllegalStateException("Переход «" + document.getStatus().getTitle()
                    + "» → «" + target.getTitle() + "» недопустим");
        }

        document.setStatus(target);
        document.setUpdatedAt(LocalDateTime.now());
        return documentRepository.save(document);
    }

    /**
     * Срок хранения по типу.
     *
     * @return дата архивации или null для документов постоянного хранения
     */
    public LocalDate retentionUntil(DocumentType type, LocalDate documentDate) {
        if (type.isPermanent() || documentDate == null) {
            return null;
        }
        return documentDate.plusYears(type.getRetentionYears());
    }

    /** Номер вида «47-пр/2026»: порядковый номер в пределах типа и года. */
    public String nextRegNumber(DocumentType type, LocalDate date) {
        long existing = documentRepository.countByDocType(type);
        int year = date.getYear();
        String candidate = (existing + 1) + "-" + type.getNumberPrefix() + "/" + year;

        // Уникальность номера проверяем явно: count не учитывает год,
        // поэтому при переходе через год возможна коллизия.
        int suffix = 1;
        while (documentRepository.existsByRegNumber(candidate)) {
            candidate = (existing + 1 + suffix) + "-" + type.getNumberPrefix() + "/" + year;
            suffix++;
        }
        return candidate;
    }
}
