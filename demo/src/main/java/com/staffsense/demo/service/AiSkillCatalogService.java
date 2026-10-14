package com.staffsense.demo.service;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.AiSkill;
import com.staffsense.demo.model.SkillRole;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.AiSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Каталог ИИ-скиллов (эпик E-08, FR-033, UC-016).
 *
 * <p>Сотрудник видит скиллы с названием, описанием и ролью, скачивает файл и
 * переходит по ссылке в репозиторий GitHub, если скилл там опубликован.
 * Наполняет каталог роль с правом администрирования.
 */
@Service
@RequiredArgsConstructor
public class AiSkillCatalogService {

    private final AiSkillRepository repository;

    /** Каталог для сотрудника: только опубликованные, при необходимости — по роли. */
    public List<AiSkill> catalog(SkillRole role) {
        return role == null
                ? repository.findByPublishedTrueOrderByNameAsc()
                : repository.findByPublishedTrueAndTargetRoleOrderByNameAsc(role);
    }

    /** Полный список для управления: видны и неопубликованные. */
    public List<AiSkill> allForManage() {
        return repository.findAllByOrderByNameAsc();
    }

    public AiSkill get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Скилл не найден: " + id));
    }

    @Transactional
    public AiSkill create(AiSkill skill, MultipartFile file, User author) {
        skill.setId(null);
        skill.setCreatedBy(author != null ? author.getId() : null);
        skill.setCreatedAt(LocalDateTime.now());
        skill.setUpdatedAt(LocalDateTime.now());
        applyFile(skill, file);
        return repository.save(skill);
    }

    @Transactional
    public AiSkill update(Long id, AiSkill form, MultipartFile file, User editor) {
        AiSkill skill = get(id);
        skill.setName(form.getName());
        skill.setDescription(form.getDescription());
        skill.setTargetRole(form.getTargetRole());
        skill.setGithubUrl(form.getGithubUrl());
        skill.setPublished(form.isPublished());
        skill.setUpdatedAt(LocalDateTime.now());
        if (editor != null && skill.getCreatedBy() == null) {
            skill.setCreatedBy(editor.getId());
        }
        // файл заменяется только если загружен новый — иначе прежний сохраняется
        applyFile(skill, file);
        return repository.save(skill);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(get(id));
    }

    /** Файл скилла для скачивания. */
    public SkillFile file(Long id) {
        AiSkill skill = get(id);
        if (!skill.hasFile()) {
            throw new ResourceNotFoundException("У скилла «" + skill.getName() + "» нет файла");
        }
        String name = skill.getFileName() != null ? skill.getFileName() : skill.getName() + ".md";
        String type = skill.getContentType() != null
                ? skill.getContentType() : "application/octet-stream";
        return new SkillFile(name, type, skill.getFileData());
    }

    private void applyFile(AiSkill skill, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        try {
            skill.setFileData(file.getBytes());
            skill.setFileName(file.getOriginalFilename());
            skill.setContentType(file.getContentType());
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать файл скилла", e);
        }
    }

    /** Файл скилла: имя, тип и содержимое. */
    public record SkillFile(String name, String contentType, byte[] data) {
    }
}
