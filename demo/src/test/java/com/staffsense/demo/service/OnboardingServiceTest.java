package com.staffsense.demo.service;

import com.staffsense.demo.dto.onboarding.ChecklistItem;
import com.staffsense.demo.dto.onboarding.ChecklistProgress;
import com.staffsense.demo.dto.onboarding.SectionSummary;
import com.staffsense.demo.model.OnboardingArticle;
import com.staffsense.demo.model.OnboardingSection;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.OnboardingArticleRepository;
import com.staffsense.demo.repository.OnboardingChecklistProgressRepository;
import com.staffsense.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты внутреннего портала онбординга (FR-042, FR-044).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OnboardingServiceTest {

    @Autowired
    private OnboardingService onboardingService;

    @Autowired
    private OnboardingArticleRepository articleRepository;

    @Autowired
    private OnboardingChecklistProgressRepository progressRepository;

    @Autowired
    private UserRepository userRepository;

    private User employee() {
        return userRepository.findByEmail("ivan@staffsense.com").orElseThrow();
    }

    @Test
    void sectionsSummaryCoversAllSections() {
        List<SectionSummary> summaries = onboardingService.getSectionSummaries(false);
        assertEquals(OnboardingSection.values().length, summaries.size());
        assertTrue(summaries.stream().allMatch(s -> s.title() != null && !s.title().isBlank()));
    }

    @Test
    void checklistHasDemoContent() {
        List<OnboardingArticle> items =
                onboardingService.getArticles(OnboardingSection.CHECKLIST, false);
        assertFalse(items.isEmpty(), "Чек-лист должен быть наполнен демо-контентом");
    }

    @Test
    void draftsAreHiddenFromEmployees() {
        OnboardingArticle draft = articleRepository.save(OnboardingArticle.builder()
                .section(OnboardingSection.ABOUT)
                .title("Черновик для теста")
                .content("текст")
                .orderIndex(99)
                .published(false)
                .build());

        List<OnboardingArticle> published =
                onboardingService.getArticles(OnboardingSection.ABOUT, false);
        assertTrue(published.stream().noneMatch(a -> a.getId().equals(draft.getId())),
                "Черновик не должен показываться сотрудникам");

        List<OnboardingArticle> all = onboardingService.getArticles(OnboardingSection.ABOUT, true);
        assertTrue(all.stream().anyMatch(a -> a.getId().equals(draft.getId())),
                "Черновик должен быть виден в режиме управления");
    }

    @Test
    void toggleChecklistItemMarksAndUnmarks() {
        User user = employee();
        OnboardingArticle item = onboardingService
                .getArticles(OnboardingSection.CHECKLIST, false).get(0);

        assertTrue(onboardingService.toggleChecklistItem(user, item.getId()), "Пункт должен отметиться");
        assertTrue(progressRepository.findByUserIdAndArticleId(user.getId(), item.getId()).isPresent());

        assertFalse(onboardingService.toggleChecklistItem(user, item.getId()), "Отметка должна сняться");
        assertTrue(progressRepository.findByUserIdAndArticleId(user.getId(), item.getId()).isEmpty());
    }

    @Test
    void progressIsPersonalAndIncreasesAfterMarking() {
        User user = employee();
        ChecklistProgress before = onboardingService.getChecklistProgress(user);

        OnboardingArticle item = onboardingService
                .getArticles(OnboardingSection.CHECKLIST, false).get(0);
        onboardingService.toggleChecklistItem(user, item.getId());

        ChecklistProgress after = onboardingService.getChecklistProgress(user);
        assertTrue(after.completed() > before.completed(), "Прогресс должен вырасти");
        assertTrue(after.percent() >= before.percent());
        assertTrue(after.percent() <= 100);
    }

    @Test
    void checklistItemsCarryCompletionState() {
        User user = employee();
        OnboardingArticle item = onboardingService
                .getArticles(OnboardingSection.CHECKLIST, false).get(0);
        onboardingService.toggleChecklistItem(user, item.getId());

        List<ChecklistItem> items = onboardingService.getChecklist(user);
        ChecklistItem marked = items.stream()
                .filter(i -> i.article().getId().equals(item.getId()))
                .findFirst()
                .orElseThrow();

        assertTrue(marked.completed());
        assertTrue(marked.completedAt() != null);
    }

    @Test
    void toggleRejectsNonChecklistArticle() {
        User user = employee();
        OnboardingArticle about = onboardingService
                .getArticles(OnboardingSection.ABOUT, false).get(0);

        assertThrows(IllegalArgumentException.class,
                () -> onboardingService.toggleChecklistItem(user, about.getId()));
    }

    @Test
    void deleteRemovesArticleAndItsProgress() {
        User user = employee();
        OnboardingArticle item = articleRepository.save(OnboardingArticle.builder()
                .section(OnboardingSection.CHECKLIST)
                .title("Временный пункт")
                .content("текст")
                .orderIndex(98)
                .published(true)
                .build());
        onboardingService.toggleChecklistItem(user, item.getId());

        onboardingService.delete(item.getId());

        assertTrue(articleRepository.findById(item.getId()).isEmpty());
        assertTrue(progressRepository.findByUserIdAndArticleId(user.getId(), item.getId()).isEmpty(),
                "Отметки удалённого пункта не должны оставаться");
    }

    /**
     * Прогресс считается только по актуальным пунктам чек-листа.
     *
     * <p>Раньше {@code completed} брался по всем разделам: перенос отмеченной статьи
     * из чек-листа в другой раздел оставлял отметку, и прогресс превышал 100 %.
     */
    @Test
    void progressIgnoresItemsMovedOutOfChecklist() {
        User user = employee();
        OnboardingArticle item = onboardingService
                .getArticles(OnboardingSection.CHECKLIST, false).get(0);

        onboardingService.toggleChecklistItem(user, item.getId());
        long afterMark = onboardingService.getChecklistProgress(user).completed();
        assertTrue(afterMark >= 1, "Отметка должна попасть в прогресс");

        // Переносим отмеченный пункт в другой раздел
        item.setSection(OnboardingSection.FAQ);
        articleRepository.saveAndFlush(item);

        ChecklistProgress progress = onboardingService.getChecklistProgress(user);
        assertEquals(afterMark - 1, progress.completed(),
                "Пункт вне чек-листа не должен учитываться в прогрессе");
        assertTrue(progress.percent() <= 100, "Прогресс не может превышать 100 %");
    }
}
