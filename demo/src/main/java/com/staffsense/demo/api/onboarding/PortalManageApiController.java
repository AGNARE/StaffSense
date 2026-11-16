package com.staffsense.demo.api.onboarding;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.OnboardingArticle;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.content.PortalContentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Управление порталом: статьи, их правка и публикация.
 *
 * <p>Отдельный контроллер рядом с {@link OnboardingApiController}: чтение
 * портала доступно всем сотрудникам и отдаёт только опубликованное, а
 * управление — HR и ADMIN и показывает черновики. Общий у них путь ресурса.
 *
 * <p>Права: {@code hasAnyRole('ADMIN','HR')} на всех ручках, включая список
 * управления: черновик статьи не должен утекать рядовому сотруднику.
 *
 * <p>Коды: {@code 201} — статья создана, {@code 200} — правка и публикация,
 * {@code 400} — некорректные данные, {@code 404} — статьи нет.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Управление порталом", description = "Статьи портала: создание, правка, публикация")
public class PortalManageApiController {

    private final PortalContentService contentService;
    private final UserRepository userRepository;

    public PortalManageApiController(PortalContentService contentService,
                                     UserRepository userRepository) {
        this.contentService = contentService;
        this.userRepository = userRepository;
    }

    /**
     * Статьи для формы управления: черновики и опубликованные, со счётчиками
     * по разделам.
     *
     * <p>Существующий {@code GET /api/v1/onboarding/articles} не подходит:
     * в нём нет признака публикации, поэтому экран не смог бы показать, что
     * статья — черновик, и не отличил бы «Опубликовать» от «Уже опубликована».
     */
    @GetMapping("/manage/articles")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public PortalManageApiDtos.ManageOverview manageArticles() {
        List<OnboardingArticle> articles = contentService.all();
        List<PortalManageApiDtos.Article> items = articles.stream()
                .map(PortalManageApiDtos::of)
                .toList();
        long drafts = items.stream().filter(item -> !item.published()).count();
        return new PortalManageApiDtos.ManageOverview(
                items.size(), drafts, contentService.sections(), items);
    }

    /** Создание статьи. По умолчанию — черновик, публикация отдельным действием. */
    @PostMapping("/articles")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<PortalManageApiDtos.Article> create(
            @Valid @RequestBody PortalManageApiDtos.SaveRequest request,
            Authentication auth) {

        OnboardingArticle article = contentService.create(request, currentUser(auth));
        return ResponseEntity
                .created(URI.create("/api/v1/onboarding/articles/" + article.getId()))
                .body(PortalManageApiDtos.of(article));
    }

    /** Правка статьи: меняются только переданные поля. */
    @PutMapping("/articles/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public PortalManageApiDtos.Article update(
            @PathVariable Long id,
            @Valid @RequestBody PortalManageApiDtos.SaveRequest request,
            Authentication auth) {

        return PortalManageApiDtos.of(contentService.update(id, request, currentUser(auth)));
    }

    /** Публикация статьи: черновик становится виден сотрудникам. */
    @PostMapping("/articles/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @ResponseStatus(HttpStatus.OK)
    public PortalManageApiDtos.Article publish(@PathVariable Long id, Authentication auth) {
        return PortalManageApiDtos.of(contentService.publish(id, currentUser(auth)));
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private User currentUser(Authentication auth) {
        if (auth == null) {
            throw new ResourceNotFoundException("Пользователь не определён");
        }
        return userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new ResourceNotFoundException("Пользователь не найден: " + auth.getName()));
    }
}
