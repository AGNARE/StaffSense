package com.staffsense.demo.api.onboarding;

import com.staffsense.demo.dto.onboarding.ChecklistProgress;
import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.OnboardingArticle;
import com.staffsense.demo.model.OnboardingSection;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.OnboardingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Портал онбординга: разделы, статьи, чек-лист новичка.
 *
 * <p>Черновики видят только ADMIN и HR — то же правило, что у серверных
 * страниц; проверка собрана в одном месте, чтобы не разъехалась между
 * разделом и статьёй.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Онбординг", description = "Портал новичка: разделы, статьи, чек-лист")
public class OnboardingApiController {

    private final OnboardingService onboardingService;
    private final UserRepository userRepository;

    public OnboardingApiController(OnboardingService onboardingService,
                                   UserRepository userRepository) {
        this.onboardingService = onboardingService;
        this.userRepository = userRepository;
    }

    /** Разделы портала со счётчиками статей. */
    @GetMapping("/sections")
    @PreAuthorize("isAuthenticated()")
    public List<OnboardingApiDtos.Section> sections(Authentication auth) {
        User actor = currentUser(auth);
        return onboardingService.getSectionSummaries(manages(actor)).stream()
                .map(s -> new OnboardingApiDtos.Section(
                        s.section().name(), s.title(), s.articleCount()))
                .toList();
    }

    /** Статьи раздела; без параметра — все доступные. */
    @GetMapping("/articles")
    @PreAuthorize("isAuthenticated()")
    public List<OnboardingApiDtos.ArticleItem> articles(
            @RequestParam(name = "section", required = false) String section,
            Authentication auth) {

        User actor = currentUser(auth);
        boolean includeDrafts = manages(actor);

        List<OnboardingArticle> articles = section == null || section.isBlank()
                ? (includeDrafts ? onboardingService.getAllArticles() : onboardingService.getLatestArticles(500))
                : onboardingService.getArticles(parseSection(section), includeDrafts);

        return articles.stream().map(this::toItem).toList();
    }

    /** Полный текст статьи. */
    @GetMapping("/articles/{id}")
    @PreAuthorize("isAuthenticated()")
    public OnboardingApiDtos.Article article(@PathVariable Long id, Authentication auth) {
        User actor = currentUser(auth);
        OnboardingArticle article = onboardingService.getArticle(id);

        // Черновик не должен открываться по прямой ссылке.
        if (!article.isPublished() && !manages(actor)) {
            throw new ResourceNotFoundException("Статья не найдена: " + id);
        }
        return new OnboardingApiDtos.Article(
                article.getId(),
                article.getSection().name(),
                article.getSection().getTitle(),
                article.getTitle(),
                article.getContent(),
                article.getUpdatedAt(),
                article.getUpdatedBy() != null ? article.getUpdatedBy().getFullName() : null);
    }

    /** Чек-лист новичка с прогрессом. */
    @GetMapping("/checklist")
    @PreAuthorize("isAuthenticated()")
    public OnboardingApiDtos.Checklist checklist(Authentication auth) {
        User actor = currentUser(auth);
        ChecklistProgress progress = onboardingService.getChecklistProgress(actor);

        List<OnboardingApiDtos.ChecklistItem> items = onboardingService.getChecklist(actor).stream()
                .map(item -> new OnboardingApiDtos.ChecklistItem(
                        item.article().getId(),
                        item.article().getTitle(),
                        item.completed(),
                        item.completedAt()))
                .toList();

        return new OnboardingApiDtos.Checklist(
                progress.total(), progress.completed(), progress.percent(), items);
    }

    /** Отметка пункта чек-листа. */
    @PostMapping("/checklist/{articleId}/toggle")
    @PreAuthorize("isAuthenticated()")
    public OnboardingApiDtos.Checklist toggle(@PathVariable Long articleId, Authentication auth) {
        User actor = currentUser(auth);
        onboardingService.toggleChecklistItem(actor, articleId);
        return checklist(auth);
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private boolean manages(User actor) {
        return actor.getRole() == Role.ADMIN || actor.getRole() == Role.HR;
    }

    private OnboardingSection parseSection(String raw) {
        try {
            return OnboardingSection.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Неизвестный раздел: " + raw);
        }
    }

    private OnboardingApiDtos.ArticleItem toItem(OnboardingArticle article) {
        return new OnboardingApiDtos.ArticleItem(
                article.getId(),
                article.getSection().name(),
                article.getSection().getTitle(),
                article.getTitle(),
                article.getOrderIndex(),
                article.getUpdatedAt(),
                article.getUpdatedBy() != null ? article.getUpdatedBy().getFullName() : null);
    }

    private User currentUser(Authentication auth) {
        if (auth == null) {
            throw new ResourceNotFoundException("Пользователь не определён");
        }
        return userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new ResourceNotFoundException("Пользователь не найден: " + auth.getName()));
    }
}
