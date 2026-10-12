package com.staffsense.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Включает планировщик задач.
 *
 * <p>Требуется для автопометки пропущенных встреч 1:1 (FR-026, BR-021):
 * ранее {@code @Scheduled} в проекте отсутствовал, поэтому признак
 * {@code missed_1on1_ratio} в ML-модели не работал.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
