package com.staffsense.demo.service.document;

import com.staffsense.demo.config.DocumentSignatureProperties;
import com.staffsense.demo.exception.DocumentTransitionException;
import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.DocumentSignature;
import com.staffsense.demo.model.DocumentStatus;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.SignatureDecision;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.DocumentRepository;
import com.staffsense.demo.repository.DocumentSignatureRepository;
import com.staffsense.demo.service.TeamAccessService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Подпись документа и срок подписи (SLA).
 *
 * <p>Маршрут ({@link DocumentRouteService}) отвечает на вопрос «где документ
 * сейчас». Этот сервис отвечает на вопрос «кто его подписал, когда и успели
 * ли»: пишет решение отдельной строкой реестра подписей, двигает статус
 * через маршрут (чтобы история оставалась полной) и держит {@code due_at}.
 *
 * <p>Правила (FR-018):
 * <ul>
 *   <li>подписывают ADMIN и HR — любые документы; PM — только документы своей
 *       команды (через {@link TeamAccessService}); EMPLOYEE и EXEC — 403;</li>
 *   <li>автор не подписывает собственный документ: самоподпись — не
 *       согласование;</li>
 *   <li>подписать можно только документ в статусе «На согласовании»;</li>
 *   <li>один сотрудник подписывает документ один раз — повтор даёт 409, а не
 *       вторую запись в реестре;</li>
 *   <li>срок подписи = момент перехода в «На согласовании» + SLA из
 *       {@code documents.signature.sla-hours}; когда документ покидает шаг,
 *       срок снимается.</li>
 * </ul>
 *
 * <p>Просрочка — сигнал, а не действие: сервис не меняет статусы по времени
 * и ничего не рассылает (почты в системе нет), этим занимается только
 * планировщик {@link DocumentSignatureSlaScheduler}, и он лишь логирует.
 */
@Service
@RequiredArgsConstructor
public class DocumentSignatureService {

    private static final Logger log = LoggerFactory.getLogger(DocumentSignatureService.class);

    /** Ограничение колонки comment; длиннее не примет БД. */
    private static final int MAX_COMMENT = 500;

    private final DocumentRepository documentRepository;
    private final DocumentSignatureRepository signatureRepository;
    private final com.staffsense.demo.service.admin.AdminAuditService auditService;
    private final DocumentRouteService routeService;
    private final DocumentAccessPolicy accessPolicy;
    private final TeamAccessService teamAccessService;
    private final DocumentSignatureProperties properties;
    private final com.staffsense.demo.service.notification.NotificationService notificationService;

    /**
     * Подписать или отклонить документ.
     *
     * <p>Порядок проверок важен для кодов ответа: роль и принадлежность — 403,
     * «не тот статус» и повторная подпись — 409. Подпись, маршрут и SLA
     * меняются в одной транзакции: если маршрут откажет, запись о подписи не
     * останется.
     *
     * @throws AccessDeniedException       роль не подписывает / чужой документ / автор
     * @throws DocumentTransitionException документ не ждёт подписи или уже подписан
     */
    @Transactional
    public DocumentSignature sign(Long documentId, User signer, SignatureDecision decision, String comment) {
        if (decision == null) {
            throw new IllegalArgumentException("Решение обязательно: APPROVED или REJECTED");
        }
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Документ не найден: " + documentId));

        requireCanSign(signer, document);

        LocalDateTime now = LocalDateTime.now();
        DocumentSignature signature = signatureRepository.save(DocumentSignature.builder()
                .document(document)
                .signer(signer)
                .decision(decision)
                .comment(normalizeComment(comment))
                .signedAt(now)
                .createdAt(now)
                .build());

        // Статус двигаем через маршрут: он проверяет переход и пишет историю,
        // поэтому в карточке остаётся и подпись, и «кто сдвинул документ».
        Document moved = routeService.changeStatus(
                signer, document.getId(), decision.targetStatus(), comment);
        // Документ покинул «На согласовании» — срока подписи у него больше нет.
        applyDeadline(moved);

        log.info("Документ {}: {} подписал как {} (роль {})",
                moved.getRegNumber(), signer.getEmail(), decision, signer.getRole());

        // Кадровое действие в журнале: подпись меняет положение документа,
        // а значит и статус связанных с ним обязательств.
        notificationService.notify(moved.getAuthor(),
                decision == SignatureDecision.APPROVED
                        ? com.staffsense.demo.model.NotificationType.DOCUMENT_SIGNED
                        : com.staffsense.demo.model.NotificationType.DOCUMENT_SIGNED,
                "Документ «" + moved.getTitle() + "»: " + decision.getTitle(),
                "Подписал: " + signer.getFullName(),
                "#/documents/" + moved.getId());
        auditService.record(
                decision == SignatureDecision.APPROVED
                        ? com.staffsense.demo.service.admin.AdminAuditService.DOCUMENT_SIGNED
                        : com.staffsense.demo.service.admin.AdminAuditService.DOCUMENT_REJECTED,
                null,
                "документ " + moved.getRegNumber() + " («" + moved.getTitle() + "»), решение " + decision);
        return signature;
    }

    /**
     * Подписи документа от ранних к поздним.
     *
     * <p>Порядок хронологический: карточка показывает таймлайн решений, а не
     * «последнее сверху» — иначе две подписи в одну минуту менялись бы местами.
     */
    @Transactional(readOnly = true)
    public List<DocumentSignature> signatures(Long documentId) {
        return signatureRepository.findByDocumentIdOrderBySignedAtAscIdAsc(documentId);
    }

    /**
     * Очередь на подпись: документы «на согласовании», видимые пользователю.
     *
     * <p>Порядок — по сроку: просроченные (самый ранний {@code due_at}) идут
     * первыми, документы без срока — в конце. Отдельной фильтрации по
     * просрочке нет: очередь показывает и то, что ещё можно успеть, и то, что
     * уже горит.
     */
    @Transactional(readOnly = true)
    public List<Document> queue(User actor) {
        requireQueueAccess(actor);

        Comparator<Document> byDueAt =
                Comparator.comparing(Document::getDueAt, Comparator.nullsLast(Comparator.naturalOrder()));

        return documentRepository.findByStatusOrderByDocumentDateDesc(DocumentStatus.ON_APPROVAL).stream()
                .filter(document -> accessPolicy.canView(actor, document))
                .sorted(byDueAt)
                .toList();
    }

    /**
     * Документы, у которых истёк срок подписи.
     *
     * <p>Выборка без фильтра видимости: её использует планировщик, которому
     * нужен общий счётчик, а не персональный список. Права на чтение
     * конкретных документов проверяются там, где они отдаются клиенту.
     */
    @Transactional(readOnly = true)
    public List<Document> overdue() {
        return documentRepository.findByStatusAndDueAtBeforeOrderByDueAtAsc(
                DocumentStatus.ON_APPROVAL, LocalDateTime.now());
    }

    /**
     * Срок подписи для текущего статуса документа.
     *
     * <p>Документ в «На согласовании» получает срок, если его ещё нет:
     * повторный вызов (или повторная отправка того же статуса) не продлевает
     * SLA — иначе «просроченный» можно было бы сбросить, просто нажав кнопку
     * ещё раз. Документ на другом шаге срока не имеет.
     *
     * @return сохранённый документ (или тот же, если менять нечего)
     */
    @Transactional
    public Document applyDeadline(Document document) {
        if (document == null || document.getId() == null) {
            return document;
        }
        boolean awaiting = document.getStatus() == DocumentStatus.ON_APPROVAL;

        if (awaiting && document.getDueAt() == null) {
            document.setDueAt(LocalDateTime.now().plus(properties.slaDuration()));
            Document saved = documentRepository.save(document);
            log.debug("Документ {}: срок подписи {}", saved.getRegNumber(), saved.getDueAt());
            return saved;
        }
        if (!awaiting && document.getDueAt() != null) {
            document.setDueAt(null);
            return documentRepository.save(document);
        }
        return document;
    }

    /**
     * Может ли пользователь подписать этот документ прямо сейчас.
     *
     * <p>Тот же набор правил, что и в {@link #requireCanSign}, но без
     * исключений: клиент рисует по этому флагу кнопки, поэтому «нет» здесь и
     * 403 в {@link #sign} обязаны совпадать.
     */
    public boolean canSign(User actor, Document document) {
        if (actor == null || actor.getRole() == null || document == null || document.getId() == null) {
            return false;
        }
        if (actor.getRole() == Role.EMPLOYEE || actor.getRole() == Role.EXEC) {
            return false;
        }
        if (isAuthor(actor, document)) {
            return false;
        }
        if (actor.getRole() == Role.PM && !isTeamDocument(actor, document)) {
            return false;
        }
        if (document.getStatus() != DocumentStatus.ON_APPROVAL) {
            return false;
        }
        return !signatureRepository.existsByDocumentIdAndSignerId(document.getId(), actor.getId());
    }

    /** Проверки подписи с исключениями — см. {@link #sign}. */
    public void requireCanSign(User actor, Document document) {
        if (actor == null || actor.getRole() == null) {
            throw new AccessDeniedException("Пользователь не определён");
        }
        if (actor.getRole() == Role.EMPLOYEE || actor.getRole() == Role.EXEC) {
            throw new AccessDeniedException("Роль " + actor.getRole() + " не подписывает документы");
        }
        if (isAuthor(actor, document)) {
            throw new AccessDeniedException("Автор не может подписать собственный документ");
        }
        if (actor.getRole() == Role.PM && !isTeamDocument(actor, document)) {
            throw new AccessDeniedException(
                    "Документ не относится к вашей команде: подписывать чужие приказы нельзя");
        }
        if (document.getStatus() != DocumentStatus.ON_APPROVAL) {
            throw new DocumentTransitionException("Документ в статусе «"
                    + document.getStatus().getTitle() + "» не ждёт подписи");
        }
        if (signatureRepository.existsByDocumentIdAndSignerId(document.getId(), actor.getId())) {
            throw new DocumentTransitionException("Документ уже подписан этим сотрудником");
        }
    }

    /** Очередь на подпись — рабочий инструмент, а не наблюдательный экран. */
    private void requireQueueAccess(User actor) {
        if (actor == null || actor.getRole() == null
                || actor.getRole() == Role.EMPLOYEE || actor.getRole() == Role.EXEC) {
            throw new AccessDeniedException("Нет доступа к очереди на подпись");
        }
    }

    private boolean isAuthor(User actor, Document document) {
        return document.getAuthor() != null
                && document.getAuthor().getId() != null
                && document.getAuthor().getId().equals(actor.getId());
    }

    /**
     * Документ своей команды: сотрудник, к которому относится документ,
     * подчинён этому PM.
     *
     * <p>Проверка идёт через {@link TeamAccessService} — единственное место,
     * где описано понятие «своя команда»; дублировать его здесь нельзя.
     */
    private boolean isTeamDocument(User pm, Document document) {
        return document.getEmployee() != null
                && teamAccessService.isEmployeeInTeamOf(pm, document.getEmployee());
    }

    /** Комментарий: обрезаем пробелы, пустой — null, длинный — ошибка 400. */
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
}
