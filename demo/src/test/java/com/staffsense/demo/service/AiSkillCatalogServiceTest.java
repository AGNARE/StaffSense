package com.staffsense.demo.service;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.AiSkill;
import com.staffsense.demo.model.SkillRole;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.AiSkillRepository;
import com.staffsense.demo.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Каталог ИИ-скиллов (эпик E-08, FR-033, UC-016).
 *
 * <p>Проверяется то, что заявлено в требовании: каталог отдаёт опубликованные
 * скиллы с названием, описанием и ролью; фильтруется по роли; файл скачивается;
 * ссылка на репозиторий необязательна; управление доступно администратору.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AiSkillCatalogServiceTest {

    @Autowired
    private AiSkillCatalogService catalogService;

    @Autowired
    private AiSkillRepository repository;

    @Autowired
    private UserRepository userRepository;

    private User admin() {
        return userRepository.findByEmail("admin@staffsense.com").orElseThrow();
    }

    private AiSkill skill(String name, SkillRole role, boolean published) {
        return AiSkill.builder()
                .name(name)
                .description("Описание скилла " + name)
                .targetRole(role)
                .published(published)
                .build();
    }

    private MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, "text/markdown",
                ("# " + name + "\nинструкция").getBytes());
    }

    @Test
    @DisplayName("Каталог отдаёт опубликованные скиллы по алфавиту")
    void catalogReturnsPublishedAlphabetically() {
        catalogService.create(skill("Якорный скилл", SkillRole.BACKEND, true), null, admin());
        catalogService.create(skill("Альфа скилл", SkillRole.BACKEND, true), null, admin());

        List<AiSkill> list = catalogService.catalog(null);

        assertTrue(list.size() >= 2);
        assertTrue(list.stream().allMatch(AiSkill::isPublished));
        assertEquals("Альфа скилл", list.get(0).getName(), "каталог отсортирован по названию");
    }

    @Test
    @DisplayName("Фильтр по роли оставляет только скиллы этой роли")
    void catalogFiltersByRole() {
        catalogService.create(skill("Для дизайнера", SkillRole.DESIGNER, true), null, admin());
        catalogService.create(skill("Для бэкенда", SkillRole.BACKEND, true), null, admin());

        List<AiSkill> design = catalogService.catalog(SkillRole.DESIGNER);

        assertFalse(design.isEmpty());
        assertTrue(design.stream().allMatch(s -> s.getTargetRole() == SkillRole.DESIGNER));
        assertTrue(design.stream().noneMatch(s -> s.getName().equals("Для бэкенда")));
    }

    @Test
    @DisplayName("Неопубликованный скилл не виден в каталоге, но виден в управлении")
    void unpublishedHiddenFromCatalogButVisibleInManage() {
        AiSkill draft = catalogService.create(
                skill("Черновик скилла", SkillRole.QA, false), null, admin());

        assertTrue(catalogService.catalog(null).stream()
                .noneMatch(s -> s.getId().equals(draft.getId())));
        assertTrue(catalogService.allForManage().stream()
                .anyMatch(s -> s.getId().equals(draft.getId())));
    }

    @Test
    @DisplayName("Создание сохраняет автора, даты и приложенный файл")
    void createStoresAuthorAndFile() {
        AiSkill created = catalogService.create(
                skill("Скилл с файлом", SkillRole.FRONTEND, true), file("skill.md"), admin());

        assertNotNull(created.getId());
        assertEquals(admin().getId(), created.getCreatedBy());
        assertNotNull(created.getCreatedAt());
        assertTrue(created.hasFile(), "файл сохранён");
        assertEquals("skill.md", created.getFileName());
    }

    @Test
    @DisplayName("Ссылка на репозиторий необязательна")
    void repositoryLinkIsOptional() {
        AiSkill withoutLink = catalogService.create(
                skill("Без ссылки", SkillRole.ANALYST, true), null, admin());
        assertFalse(withoutLink.hasRepository());

        AiSkill withLink = skill("Со ссылкой", SkillRole.ANALYST, true);
        withLink.setGithubUrl("https://github.com/AGNARE/StaffSense");
        AiSkill saved = catalogService.create(withLink, null, admin());
        assertTrue(saved.hasRepository());
    }

    @Test
    @DisplayName("Скачивание отдаёт файл, а без файла — понятную ошибку")
    void downloadReturnsFileOrFails() {
        AiSkill withFile = catalogService.create(
                skill("Скачиваемый", SkillRole.PM, true), file("pm-skill.md"), admin());

        AiSkillCatalogService.SkillFile result = catalogService.file(withFile.getId());
        assertEquals("pm-skill.md", result.name());
        assertTrue(result.data().length > 0);

        AiSkill noFile = catalogService.create(
                skill("Без файла", SkillRole.PM, true), null, admin());
        assertThrows(ResourceNotFoundException.class, () -> catalogService.file(noFile.getId()));
    }

    @Test
    @DisplayName("Правка меняет поля и сохраняет прежний файл, если новый не загружен")
    void updateKeepsFileWhenNoNewOneUploaded() {
        AiSkill created = catalogService.create(
                skill("Исходный", SkillRole.BACKEND, true), file("old.md"), admin());

        AiSkill form = skill("Переименованный", SkillRole.QA, false);
        AiSkill updated = catalogService.update(created.getId(), form, null, admin());

        assertEquals("Переименованный", updated.getName());
        assertEquals(SkillRole.QA, updated.getTargetRole());
        assertFalse(updated.isPublished());
        assertTrue(updated.hasFile(), "прежний файл не потерян");
        assertEquals("old.md", updated.getFileName());
    }

    @Test
    @DisplayName("Удаление убирает скилл из каталога")
    void deleteRemovesSkill() {
        AiSkill created = catalogService.create(
                skill("Временный", SkillRole.OTHER, true), null, admin());
        Long id = created.getId();

        catalogService.delete(id);

        assertThrows(ResourceNotFoundException.class, () -> catalogService.get(id));
    }

    @Test
    @DisplayName("Обращение к несуществующему скиллу даёт понятную ошибку")
    void missingSkillGivesClearError() {
        assertThrows(ResourceNotFoundException.class, () -> catalogService.get(999_999L));
    }
}
