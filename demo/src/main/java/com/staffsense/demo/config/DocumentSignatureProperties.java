package com.staffsense.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Правила подписи документов (SLA).
 *
 * <p>Задаются в {@code application.yml} новым блоком:
 * <pre>
 * documents:
 *   signature:
 *     sla-hours: 24
 * </pre>
 *
 * <p>Срок подписи — политика компании, а не константа кода: 24 часа по
 * умолчанию, но кадровая служба может ужесточить или ослабить его без
 * пересборки. Значение читает {@code DocumentSignatureService}, когда
 * документ переходит в «На согласовании».
 *
 * <p>Нулевое или отрицательное значение считается ошибкой конфигурации и
 * заменяется дефолтом: иначе документ становился бы просроченным в момент
 * отправки на подпись.
 */
@ConfigurationProperties(prefix = "documents.signature")
public class DocumentSignatureProperties {

    /** Дефолт из ТЗ: сутки на решение. */
    public static final long DEFAULT_SLA_HOURS = 24;

    /** Сколько часов документ ждёт подписи. */
    private long slaHours = DEFAULT_SLA_HOURS;

    public long getSlaHours() {
        return slaHours;
    }

    public void setSlaHours(long slaHours) {
        this.slaHours = slaHours;
    }

    /** Срок подписи как длительность; неположительное значение — дефолт. */
    public Duration slaDuration() {
        return Duration.ofHours(slaHours > 0 ? slaHours : DEFAULT_SLA_HOURS);
    }
}
