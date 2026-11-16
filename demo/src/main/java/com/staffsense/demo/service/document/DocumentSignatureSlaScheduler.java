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

    @Scheduled(cron = "${documents.signature.overdue-cron:0 0 * * * *}")
    public void reportOverdueSignatures() {
        try {
            List<Document> overdue = signatureService.overdue();
            if (overdue.isEmpty()) {
                log.info("SLA подписи: просроченных документов нет");
                return;
            }

            // Считаем, а не рассылаем: единственный потребитель — человек,
            // который читает лог и решает, эскалировать ли вручную.
            log.warn("SLA подписи: просрочено документов {}, самый старый — {} (срок {})",
                    overdue.size(),
                    overdue.get(0).getRegNumber(),
                    overdue.get(0).getDueAt() != null
                            ? overdue.get(0).getDueAt().format(TIME_FORMAT) : "—");
        } catch (Exception e) {
            // Плановая задача не должна ронять приложение.
            log.error("Плановая проверка SLA подписи завершилась ошибкой: {}", e.getMessage(), e);
        }
    }
}
