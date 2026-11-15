package com.staffsense.demo.service.document;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.DocumentStatus;
import com.staffsense.demo.model.DocumentStatusHistory;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.DocumentRepository;
import com.staffsense.demo.repository.DocumentStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Маршрут документа: входящие, история переходов и смена статуса.
 *
 * <p>Отдельный сервис от {@link com.staffsense.demo.service.DocumentService}
 * потому, что у реестра и у маршрута разные обязанности: реестр отвечает за
 * номер и срок хранения, маршрут — за то, кто на каком шаге действует и что
 * об этом останется в истории. Реестр (и его тесты) при этом не меняется.
 *
 * <p>Права проверяет {@link DocumentAccessPolicy}: сюда не попадает ни один
 * переход, который роль не может выполнить.
 */
@Service
@RequiredArgsConstructor
public class DocumentRouteService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRouteService.class);

    /** Ограничение колонки comment; длиннее не примет БД. */
    private static final int MAX_COMMENT = 500;

    private final DocumentRepository documentRepository;
    private final DocumentStatusHistoryRepository historyRepository;
    private final DocumentAccessPolicy accessPolicy;

    /**
     * Входящие: документы, ожидающие решения по маршруту.
     *
     * <p>Для HR и ADMIN это документы «на согласовании» — их подпись держит
     * маршрут. PM видит только документы своей команды, а значит не узнает
     * о приказах чужих отделов даже по счётчику. EXEC переходов не выполняет,
     * поэтому его список пуст; EMPLOYEE получает 403.
     */
    @Transactional(readOnly = true)
    public List<Document> inbox(User actor) {
        accessPolicy.requireInboxAccess(actor);

        if (actor.getRole() == Role.EXEC) {
            return List.of();
        }

        List<Document> awaiting = documentRepository
                .findByStatusOrderByDocumentDateDesc(DocumentStatus.ON_APPROVAL)
                .stream()
                .filter(accessPolicy::awaitsDecision)
                .toList();

        if (actor.getRole() == Role.PM) {
            return awaiting.stream()
                    .filter(document -> accessPolicy.canView(actor, document))
                    .toList();
        }
        return awaiting;
    }

    /** Карточка: документ, если он виден пользователю. */
    @Transactional(readOnly = true)
    public Document requireVisible(Long id, User actor) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Документ не найден: " + id));
        accessPolicy.requireCanView(actor, document);
        return document;
    }

    /** История переходов от ранних записей к поздним. */
    @Transactional(readOnly = true)
    public List<DocumentStatusHistory> history(Long documentId) {
        return historyRepository.findByDocumentIdOrderByCreatedAtAscIdAsc(documentId);
    }

    /**
     * Смена статуса с записью в историю.
     *
     * <p>Повторная установка того же статуса ошибкой не считается (так было
     * и в реестре), но и запись в историю не создаёт: дубль «утверждён →
     * утверждён» только засорил бы таймлайн.
     */
    @Transactional
    public Document changeStatus(User actor, Long id, DocumentStatus target, String comment) {
        Document document = requireVisible(id, actor);

        if (target != null && target == document.getStatus()) {
            return document;
        }
        accessPolicy.requireCanTransition(actor, document, target);

        DocumentStatus from = document.getStatus();
        document.setStatus(target);
        document.setUpdatedAt(LocalDateTime.now());
        Document saved = documentRepository.save(document);

        historyRepository.save(DocumentStatusHistory.builder()
                .document(saved)
                .fromStatus(from)
                .toStatus(target)
                .actor(actor)
                .comment(normalizeComment(comment))
                .createdAt(LocalDateTime.now())
                .build());

        log.info("Документ {}: {} → {} (актор {}, роль {})",
                saved.getRegNumber(), from, target,
                actor.getEmail(), actor.getRole());
        return saved;
    }

    /**
     * Первая запись истории при регистрации документа.
     *
     * <p>Без неё карточка нового документа начиналась бы с пустого таймлайна,
     * хотя документ уже кто-то создал.
     */
    @Transactional
    public void recordCreated(Document document, User actor) {
        historyRepository.save(DocumentStatusHistory.builder()
                .document(document)
                .fromStatus(null)
                .toStatus(document.getStatus())
                .actor(actor)
                .comment("Документ зарегистрирован")
                .createdAt(document.getCreatedAt() != null ? document.getCreatedAt() : LocalDateTime.now())
                .build());
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_COMMENT) {
            throw new IllegalArgumentException("Комментарий длиннее " + MAX_COMMENT + " символов");
        }
        return trimmed;
    }

    /**
     * Проверка, что пользователь вообще имеет дело с реестром.
     *
     * <p>EMPLOYEE отсекается ещё на {@code @PreAuthorize} контроллера; дубль
     * здесь — страховка на случай прямого вызова сервиса.
     */
    public void requireRegistryAccess(User actor) {
        if (actor == null || actor.getRole() == null || actor.getRole() == Role.EMPLOYEE) {
            throw new AccessDeniedException("Нет доступа к реестру документов");
        }
    }
}
