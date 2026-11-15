package com.staffsense.demo.api.document;

import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.DocumentStatus;
import com.staffsense.demo.model.DocumentStatusHistory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Карточка документа: полные данные, история переходов и действия,
 * доступные текущему пользователю.
 *
 * <p>Отличается от строки реестра тем, ради чего экран и открывают: видно,
 * на каком шаге документ стоит, кто и когда его туда перевёл, и какие кнопки
 * маршрута нажимать. Список действий считает сервер
 * ({@code DocumentAccessPolicy}) — клиент его не повторяет.
 */
public record DocumentCardDto(
        Long id,
        String regNumber,
        String type,
        String typeTitle,
        String title,
        String summary,
        Long employeeId,
        String employeeName,
        String employeeDepartment,
        String employeePosition,
        String authorName,
        LocalDate documentDate,
        LocalDate retentionUntil,
        boolean permanentRetention,
        String status,
        String statusTitle,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<HistoryEntry> history,
        List<Transition> allowedTransitions
) {

    /** Один переход маршрута в таймлайне карточки. */
    public record HistoryEntry(
            Long id,
            String fromStatus,
            String fromStatusTitle,
            String toStatus,
            String toStatusTitle,
            String actorName,
            String comment,
            LocalDateTime createdAt
    ) {
        public static HistoryEntry from(DocumentStatusHistory entry) {
            DocumentStatus from = entry.getFromStatus();
            DocumentStatus to = entry.getToStatus();
            return new HistoryEntry(
                    entry.getId(),
                    from != null ? from.name() : null,
                    from != null ? from.getTitle() : null,
                    to != null ? to.name() : null,
                    to != null ? to.getTitle() : null,
                    entry.getActor() != null ? entry.getActor().getFullName() : "Система",
                    entry.getComment(),
                    entry.getCreatedAt());
        }
    }

    /** Доступный переход: целевой статус и его подпись. */
    public record Transition(String status, String title) {
        public static Transition of(DocumentStatus status) {
            return new Transition(status.name(), status.getTitle());
        }
    }

    public static DocumentCardDto from(Document document,
                                       List<DocumentStatusHistory> history,
                                       List<DocumentStatus> allowedTransitions) {
        return new DocumentCardDto(
                document.getId(),
                document.getRegNumber(),
                document.getDocType().name(),
                document.getDocType().getTitle(),
                document.getTitle(),
                document.getSummary(),
                document.getEmployee() != null ? document.getEmployee().getId() : null,
                document.getEmployee() != null ? document.getEmployee().getFullName() : null,
                document.getEmployee() != null ? document.getEmployee().getDepartment() : null,
                document.getEmployee() != null ? document.getEmployee().getPosition() : null,
                document.getAuthor() != null ? document.getAuthor().getFullName() : null,
                document.getDocumentDate(),
                document.getRetentionUntil(),
                document.getDocType().isPermanent(),
                document.getStatus().name(),
                document.getStatus().getTitle(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                history.stream().map(HistoryEntry::from).toList(),
                allowedTransitions.stream().map(Transition::of).toList());
    }
}
