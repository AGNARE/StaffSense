package com.staffsense.demo.service;

import com.staffsense.demo.dto.onboarding.ChecklistItem;
import com.staffsense.demo.dto.onboarding.ChecklistProgress;
import com.staffsense.demo.dto.onboarding.SectionSummary;
import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.OnboardingArticle;
import com.staffsense.demo.model.OnboardingChecklistProgress;
import com.staffsense.demo.model.OnboardingSection;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.OnboardingArticleRepository;
import com.staffsense.demo.repository.OnboardingChecklistProgressRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Внутренний портал онбординга (модуль B).
 *
 * <p>FR-042 — статьи портала; FR-044 — чек-лист новичка с персональными отметками.
 * Чтение доступно всем сотрудникам, изменение — HR и ADMIN (проверяется в контроллере).
 */
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private final OnboardingArticleRepository articleRepository;
    private final OnboardingChecklistProgressRepository progressRepository;

    // ── Чтение ───────────────────────────────────────────────────────────

    /** Сводка по всем разделам: название и число опубликованных статей. */
    public List<SectionSummary> getSectionSummaries(boolean includeDrafts) {
        List<SectionSummary> summaries = new ArrayList<>();
        for (OnboardingSection section : OnboardingSection.values()) {
            long count = includeDrafts
                    ? articleRepository.countBySection(section)
                    : articleRepository.countBySectionAndPublishedTrue(section);
            summaries.add(new SectionSummary(section, section.getTitle(), count));
        }
        return summaries;
    }

    /** Статьи раздела. Черновики видны только при {@code includeDrafts = true}. */
    public List<OnboardingArticle> getArticles(OnboardingSection section, boolean includeDrafts) {
        return includeDrafts
                ? articleRepository.findBySectionOrderByOrderIndexAsc(section)
                : articleRepository.findBySectionAndPublishedTrueOrderByOrderIndexAsc(section);
    }

    /** Последние опубликованные статьи для главной страницы портала. */
    public List<OnboardingArticle> getLatestArticles(int limit) {
        List<OnboardingArticle> all = articleRepository.findByPublishedTrueOrderBySectionAscOrderIndexAsc();
        return all.stream().limit(Math.max(0, limit)).toList();
    }

    public OnboardingArticle getArticle(Long id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Статья не найдена: " + id));
    }

    public List<OnboardingArticle> getAllArticles() {
        return articleRepository.findAllByOrderBySectionAscOrderIndexAsc();
    }

    // ── Чек-лист новичка ─────────────────────────────────────────────────

    /** Пункты чек-листа с персональными отметками сотрудника. */
    public List<ChecklistItem> getChecklist(User user) {
        List<OnboardingArticle> items =
                articleRepository.findBySectionAndPublishedTrueOrderByOrderIndexAsc(OnboardingSection.CHECKLIST);

        Map<Long, OnboardingChecklistProgress> progressByArticle = progressRepository.findByUserId(user.getId())
                .stream()
                .collect(Collectors.toMap(p -> p.getArticle().getId(), Function.identity(), (a, b) -> a));

        return items.stream()
                .map(item -> {
                    OnboardingChecklistProgress progress = progressByArticle.get(item.getId());
                    return new ChecklistItem(item, progress != null,
                            progress != null ? progress.getCompletedAt() : null);
                })
                .toList();
    }

    public ChecklistProgress getChecklistProgress(User user) {
        long total = articleRepository
                .countBySectionAndPublishedTrue(OnboardingSection.CHECKLIST);
        // Считаем только отметки по актуальным пунктам чек-листа: иначе после переноса
        // отмеченной статьи в другой раздел completed превышал total и прогресс был > 100 %.
        long completed = progressRepository
                .countByUserIdAndArticleSectionAndArticlePublishedTrue(user.getId(),
                        OnboardingSection.CHECKLIST);
        return ChecklistProgress.of(total, completed);
    }

    /**
     * Переключить отметку пункта чек-листа.
     * Отметка персональная: у каждого сотрудника свой прогресс.
     */
    @Transactional
    public boolean toggleChecklistItem(User user, Long articleId) {
        OnboardingArticle article = getArticle(articleId);
        if (article.getSection() != OnboardingSection.CHECKLIST) {
            throw new IllegalArgumentException("Статья не является пунктом чек-листа");
        }

        Optional<OnboardingChecklistProgress> existing =
                progressRepository.findByUserIdAndArticleId(user.getId(), articleId);

        if (existing.isPresent()) {
            progressRepository.delete(existing.get());
            log.debug("Checklist item {} unmarked for user {}", articleId, user.getId());
            return false;
        }

        progressRepository.save(OnboardingChecklistProgress.builder()
                .user(user)
                .article(article)
                .completedAt(LocalDateTime.now())
                .build());
        log.debug("Checklist item {} marked for user {}", articleId, user.getId());
        return true;
    }

    // ── Управление (HR/ADMIN) ────────────────────────────────────────────

    @Transactional
    public OnboardingArticle save(OnboardingArticle article, User actor) {
        article.setUpdatedBy(actor);
        article.setUpdatedAt(LocalDateTime.now());
        return articleRepository.save(article);
    }

    @Transactional
    public OnboardingArticle update(Long id, OnboardingArticle form, User actor) {
        OnboardingArticle article = getArticle(id);
        article.setSection(form.getSection());
        article.setTitle(form.getTitle());
        article.setContent(form.getContent());
        article.setOrderIndex(form.getOrderIndex() != null ? form.getOrderIndex() : 0);
        article.setPublished(form.isPublished());
        article.setUpdatedBy(actor);
        article.setUpdatedAt(LocalDateTime.now());
        return articleRepository.save(article);
    }

    /** Удаление статьи вместе с отметками чек-листа (FK без каскада). */
    @Transactional
    public void delete(Long id) {
        OnboardingArticle article = getArticle(id);
        progressRepository.deleteByArticleId(article.getId());
        articleRepository.delete(article);
        log.info("Onboarding article {} deleted", id);
    }
}
