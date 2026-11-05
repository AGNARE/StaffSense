package com.staffsense.demo.repository;

import com.staffsense.demo.model.RiskThresholdSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Настройка порогов риска: одна строка {@code id = 1} (FR-039).
 */
@Repository
public interface RiskThresholdSettingRepository
        extends JpaRepository<RiskThresholdSetting, Long> {
}
