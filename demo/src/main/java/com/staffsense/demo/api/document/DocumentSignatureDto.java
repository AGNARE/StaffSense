package com.staffsense.demo.api.document;

import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.DocumentSignature;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Подписи документа и очередь на подпись — то, чего не хватало маршруту.
 *
 * <p>Класс держит только представления (records), без логики доступа: права
 * считает {@code DocumentSignatureService}, а здесь они лишь превращаются в
 * JSON. Исключение — расчёт остатка часов и признака просрочки: это чистая
 * арифметика над {@code due_at}, и она обязана быть одинаковой и в очереди,
 * и в карточке.
 */
public final class DocumentSignatureDto {

    private DocumentSignatureDto() {
    }

    /** Одна подпись в карточке документа. */
    public record Entry(
            Long id,
            Long signerId,
            String signerName,
            String decision,
            String decisionTitle,
            String comment,
            LocalDateTime signedAt
    ) {
        public static Entry from(DocumentSignature signature) {
            return new Entry(
                    signature.getId(),
                    signature.getSigner() != null ? signature.getSigner().getId() : null,
                    signature.getSigner() != null ? signature.getSigner().getFullName() : "—",
                    signature.getDecision().name(),
                    signature.getDecision().getTitle(),
                    signature.getComment(),
                    signature.getSignedAt());
        }

        public static List<Entry> from(List<DocumentSignature> signatures) {
            return signatures.stream().map(Entry::from).toList();
        }
    }

    /**
     * Строка очереди на подпись.
     *
     * <p>Просрочка и остаток часов считаются на сервере: клиент не должен
     * сравнивать {@code dueAt} с часами своего ноутбука — в другом часовом
     * поясе или с сбитыми часами очередь выглядела бы иначе, чем её видит
     * проверяющий.
     */
    public record QueueItem(
            Long id,
            String regNumber,
            String type,
            String typeTitle,
            String title,
            String employeeName,
            String authorName,
            String status,
            String statusTitle,
            LocalDateTime dueAt,
            boolean overdue,
            Long remainingHours,
            boolean canSign
    ) {
        public static QueueItem from(Document document, LocalDateTime now, boolean canSign) {
            LocalDateTime dueAt = document.getDueAt();
            return new QueueItem(
                    document.getId(),
                    document.getRegNumber(),
                    document.getDocType().name(),
                    document.getDocType().getTitle(),
                    document.getTitle(),
                    document.getEmployee() != null ? document.getEmployee().getFullName() : null,
                    document.getAuthor() != null ? document.getAuthor().getFullName() : null,
                    document.getStatus().name(),
                    document.getStatus().getTitle(),
                    dueAt,
                    isOverdue(dueAt, now),
                    hoursLeft(dueAt, now),
                    canSign);
        }
    }

    /** Просрочен ли срок подписи; без срока просрочки нет. */
    public static boolean isOverdue(LocalDateTime dueAt, LocalDateTime now) {
        return dueAt != null && dueAt.isBefore(now);
    }

    /**
     * Остаток часов до срока.
     *
     * <p>Для будущего срока округляем вниз (23 ч 40 мин — «осталось 23 ч»),
     * для прошедшего — вверх по модулю (просрочка 2 ч 10 мин — «−3 ч»):
     * так ноль не выглядит как «время ещё есть».
     *
     * @return часы или {@code null}, если срока нет
     */
    public static Long hoursLeft(LocalDateTime dueAt, LocalDateTime now) {
        if (dueAt == null) {
            return null;
        }
        long minutes = Duration.between(now, dueAt).toMinutes();
        if (minutes >= 0) {
            return minutes / 60;
        }
        return -((-minutes + 59) / 60);
    }
}
