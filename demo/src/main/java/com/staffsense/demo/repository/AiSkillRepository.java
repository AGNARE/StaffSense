package com.staffsense.demo.repository;

import com.staffsense.demo.model.AiSkill;
import com.staffsense.demo.model.SkillRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Каталог ИИ-скиллов (FR-033, UC-016).
 */
public interface AiSkillRepository extends JpaRepository<AiSkill, Long> {

    /** Каталог целиком, по алфавиту. */
    List<AiSkill> findByPublishedTrueOrderByNameAsc();

    /** Каталог с фильтром по роли. */
    List<AiSkill> findByPublishedTrueAndTargetRoleOrderByNameAsc(SkillRole targetRole);

    /** Управление каталогом: видны и неопубликованные. */
    List<AiSkill> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
