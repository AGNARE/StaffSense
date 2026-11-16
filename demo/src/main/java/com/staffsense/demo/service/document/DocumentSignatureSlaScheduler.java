package com.staffsense.demo.service.document;

import com.staffsense.demo.model.Document;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Плановый отчёт по просроченным подписям (SLA).
 *
 * <p>Раз в час считает документы, у которых {@code due_at} прошёл, и пишет
 * число в лог. Никаких действий: статусы не меняются, письма не уходят —
 * почты в системе нет, а просрочка по ТЗ является сигналом, а не командой.
 * Эскалация — осознанно ручное решение кадровой службы, автоматика не должна
 * принимать его за неё.
 *
 * <p>Расписание переопределяется свойством
 * {@code documents.signature.overdue-cron} (по умолчанию — начало каждого
 * часа). Ошибка задачи логируется и не роняет приложение.
 */
@Component
@RequiredArgsConstructor
public class DocumentSignatureSlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(DocumentSignatureSlaScheduler.class);

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final DocumentSignatureService signatureService;
    private final com.staffsense.demo.service.notification.NotificationService notificationService;
    private final com.staffsense.demo.repository.UserRepository userRepository;

    @Scheduled(cron = "${documents.signature.overdue-cron:0 0 * * * *}")
    public void reportOverdueSignatures() {
        try {
            List<Document> overdue = signatureService.overdue();
            if (overdue.isEmpty()) {
                log.info("SLA подписи: просроченных документов нет");
                return;
            }

            log.warn("SLA подписи: просрочено документов {}, самый старый — {} (срок {})",
                    overdue.size(),
                    overdue.get(0).getRegNumber(),
                    overdue.get(0).getDueAt() != null
                            ? overdue.get(0).getDueAt().format(TIME_FORMAT) : "—");

            // Просрочка — повод для человека, поэтому не только лог: те, кто
            // может подписать, получают уведомление в системе. Статусы при
            // этом не меняем: эскалация остаётся решением кадровой службы.
            var signers = userRepository.findByActive(true).stream()
                    .filter(u -> u.getRole() == com.staffsense.demo.model.Role.ADMIN
                            || u.getRole() == com.staffsense.demo.model.Role.HR)
                    .toList();

            for (Document document : overdue) {
                notificationService.notifyAll(signers,
                        com.staffsense.demo.model.NotificationType.SIGNATURE_OVERDUE,
                        "Истёк срок подписи: " + document.getTitle(),
                        "№ " + document.getRegNumber() + ", срок "
                                + (document.getDueAt() != null
                                        ? document.getDueAt().format(TIME_FORMAT) : "не задан"),
                        "#/documents/signature");
            }
        } catch (Exception e) {
            // Плановая задача не должна ронять приложение.
            log.error("Плановая проверка SLA подписи завершилась ошибкой: {}", e.getMessage(), e);
        }
    }
}
