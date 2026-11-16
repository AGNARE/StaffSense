package com.staffsense.demo.service.document;

import com.staffsense.demo.exception.DocumentTransitionException;
import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.DocumentStatus;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.service.TeamAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Кто и что может делать с документом реестра.
 *
 * <p>Матрица доступа (FR-018):
 * <ul>
 *   <li><b>ADMIN, HR</b> — полный доступ: видят все документы и выполняют
 *       любой допустимый маршрутом переход;</li>
 *   <li><b>PM</b> — только документы своей команды (через
 *       {@link TeamAccessService}): сотрудник считается своим, если
 *       {@code employee.manager.id == pm.id}, плюс собственная карточка PM.
 *       PM ведёт первые шаги маршрута, но не архивирует документы;</li>
 *   <li><b>EXEC</b> — читает всё, но не выполняет переходов: наблюдательная
 *       роль без операционных действий;</li>
 *   <li><b>EMPLOYEE</b> — доступа к реестру документов нет (403): там
 *       кадровые приказы и договоры коллег.</li>
 * </ul>
 *
 * <p>Правила живут здесь, а не в контроллере, потому что ими пользуются и
 * входящие, и карточка, и смена статуса — три места, которые не должны
 * разойтись.
 */
@Service
@RequiredArgsConstructor
public class DocumentAccessPolicy {

    /** Статусы, на которых документ ждёт решения по маршруту. */
    private static final Set<DocumentStatus> INBOX_STATUSES =
            EnumSet.of(DocumentStatus.ON_APPROVAL);

    /**
     * Переходы, которые PM выполняет по документам своей команды.
     *
     * <p>Ключ — текущий статус: PM отправляет черновик на согласование,
     * утверждает или возвращает документ с согласования. Архив — не его шаг,
     * поэтому из «В архиве» и «Утверждён» PM не двигает документ.
     */
    private static final Map<DocumentStatus, Set<DocumentStatus>> PM_ROUTE =
            Map.of(
                    DocumentStatus.DRAFT, EnumSet.of(DocumentStatus.ON_APPROVAL),
                    DocumentStatus.ON_APPROVAL,
                            EnumSet.of(DocumentStatus.DRAFT, DocumentStatus.APPROVED));

    private final TeamAccessService teamAccessService;

    /** Виден ли документ пользователю. */
    public boolean canView(User actor, Document document) {
        if (actor == null || document == null || actor.getRole() == null) {
            return false;
        }
        return switch (actor.getRole()) {
            case ADMIN, HR, EXEC -> true;
            case PM -> document.getEmployee() != null
                    && teamAccessService.canViewEmployee(actor, document.getEmployee());
            case EMPLOYEE -> false;
        };
    }

    public void requireCanView(User actor, Document document) {
        if (!canView(actor, document)) {
            throw new AccessDeniedException(
                    "Нет доступа к документу ID=" + (document != null ? document.getId() : null));
        }
    }

    /**
     * Отбрасывает документы, не видимые пользователю.
     *
     * <p>Нужен спискам: реестр — такая же точка доступа, как карточка, и PM
     * не должен увидеть чужие приказы хотя бы строкой.
     */
    public List<Document> filterVisible(User actor, List<Document> documents) {
        return documents.stream().filter(document -> canView(actor, document)).toList();
    }

    /** Входящие доступны всем, кому доступен реестр, кроме EMPLOYEE. */
    public void requireInboxAccess(User actor) {
        if (actor == null || actor.getRole() == null || actor.getRole() == Role.EMPLOYEE) {
            throw new AccessDeniedException("Нет доступа к входящим документам");
        }
    }

    /** Ждёт ли документ решения на этом шаге (для входящих). */
    public boolean awaitsDecision(Document document) {
        return document != null && INBOX_STATUSES.contains(document.getStatus());
    }

    /**
     * Допустимые для роли переходы из текущего статуса.
     *
     * <p>Пересечение двух правил: что разрешает маршрут
     * ({@link DocumentStatus#canTransitionTo}) и что разрешено роли. Именно
     * этот список клиент рисует кнопками, поэтому он не должен быть шире
     * серверной проверки.
     */
    public List<DocumentStatus> allowedTransitions(User actor, Document document) {
        if (!canView(actor, document) || document.getStatus() == null) {
            return List.of();
        }
        DocumentStatus from = document.getStatus();
        List<DocumentStatus> targets = new ArrayList<>();

        switch (actor.getRole()) {
            case ADMIN, HR -> {
                for (DocumentStatus target : DocumentStatus.values()) {
                    if (target != from && from.canTransitionTo(target)) {
                        targets.add(target);
                    }
                }
            }
            case PM -> {
                for (DocumentStatus target : PM_ROUTE.getOrDefault(from, Set.of())) {
                    if (target != from && from.canTransitionTo(target)) {
                        targets.add(target);
                    }
                }
            }
            case EXEC, EMPLOYEE -> {
                // Наблюдатель и сотрудник переходов не выполняют.
            }
        }
        return targets;
    }

    /**
     * Проверка перехода перед записью.
     *
     * @throws AccessDeniedException       роль не имеет права на этот шаг
     * @throws DocumentTransitionException маршрут не допускает такой переход
     */
    public void requireCanTransition(User actor, Document document, DocumentStatus target) {
        requireCanView(actor, document);
        if (target == null) {
            throw new DocumentTransitionException("Не указан целевой статус документа");
        }
        if (allowedTransitions(actor, document).contains(target)) {
            return;
        }
        if (!document.getStatus().canTransitionTo(target)) {
            throw new DocumentTransitionException("Переход «" + document.getStatus().getTitle()
                    + "» → «" + target.getTitle() + "» недопустим по маршруту");
        }
        throw new AccessDeniedException("Роль " + actor.getRole()
                + " не может перевести документ в статус «" + target.getTitle() + "»");
    }
}
