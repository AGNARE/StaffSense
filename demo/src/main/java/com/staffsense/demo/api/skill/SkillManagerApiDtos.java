package com.staffsense.demo.api.skill;

import com.staffsense.demo.model.AiSkill;
import com.staffsense.demo.model.SkillRole;

import java.util.Arrays;
import java.util.List;

/**
 * DTO управления каталогом ИИ-скиллов (FR-033).
 *
 * <p><b>Почему ответ мутаций — это {@link SkillApiController.SkillDto}, а не
 * отдельный тип.</b> Скилл в контракте один: и каталог, и карточка, и ответ
 * создания/правки/публикации описывают его одинаково. Второй record той же
 * формы пришлось бы держать синхронным вручную, и клиент получил бы два
 * несовместимых типа одной сущности. Поэтому здесь только фабрика, которая
 * собирает уже существующий DTO.
 *
 * <p>Содержимое файла в DTO не попадает — по-прежнему: файл отдаётся
 * существующей ручкой {@code GET /api/v1/skills/{id}/file}.
 */
public final class SkillManagerApiDtos {

    private SkillManagerApiDtos() {
    }

    /**
     * Роль для выпадающего списка формы: значение enum и русское название.
     *
     * <p>Нужен клиенту, чтобы не дублировать {@link SkillRole} у себя: набор
     * ролей — доменное решение, а не оформление.
     */
    public record RoleOption(String value, String title) {
    }

    /** Все роли каталога в порядке объявления enum. */
    public static List<RoleOption> roles() {
        return Arrays.stream(SkillRole.values())
                .map(role -> new RoleOption(role.name(), role.getTitle()))
                .toList();
    }

    /** Метаданные скилла в той же форме, что отдаёт каталог. */
    public static SkillApiController.SkillDto skill(AiSkill skill) {
        return new SkillApiController.SkillDto(
                skill.getId(),
                skill.getName(),
                skill.getDescription(),
                skill.getTargetRole().name(),
                skill.getTargetRole().getTitle(),
                skill.getFileName(),
                skill.getGithubUrl(),
                skill.isPublished(),
                skill.getUpdatedAt());
    }
}
