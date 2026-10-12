package com.staffsense.demo.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Плановая пометка пропущенных встреч 1:1 (FR-026, BR-021).
 *
 * <p>По умолчанию выполняется ежедневно в 03:00; расписание настраивается
 * свойством {@code staffsense.oneonone.missed-cron}.
 */
@Component
@RequiredArgsConstructor
public class OneOnOneMissedScheduler {

    private static final Logger log = LoggerFactory.getLogger(OneOnOneMissedScheduler.class);

    private final OneOnOneService oneOnOneService;

    @Scheduled(cron = "${staffsense.oneonone.missed-cron:0 0 3 * * *}")
    public void markOverdueMeetingsAsMissed() {
        try {
            int updated = oneOnOneService.markOverdueAsMissed();
            log.info("Scheduled missed-meeting check finished, updated={}", updated);
        } catch (Exception e) {
            // Плановая задача не должна ронять приложение.
            log.error("Scheduled missed-meeting check failed: {}", e.getMessage(), e);
        }
    }
}
