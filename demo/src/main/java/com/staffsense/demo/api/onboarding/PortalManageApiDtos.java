package com.staffsense.demo.api.onboarding;

import com.staffsense.demo.model.OnboardingArticle;
import com.staffsense.demo.model.OnboardingSection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Контракт «Управления порталом»: статьи создаются, правятся и публикуются.
 *
 * <p>Чтение портала живёт в {@link OnboardingApiDtos} и {@code OnboardingApiController};
 * здесь — только запись, потому что у неё другие права (ADMIN/HR) и другая
 * модель: форма управления показывает черновик, раздел, порядок и признак
 * публикации, а карточка сотрудника — только опубликованный текст.
 */
public final class PortalManageApiDtos {

    private PortalManageApiDtos() {
    }

    /** Тело создания статьи и её правки. */
    public record SaveRequest(
            @NotNull(message = "Раздел портала обязателен") OnboardingSection section,

            @NotBlank(message = "Заголовок статьи обязателен")
            @Size(max = 200, message = "Заголовок длиннее 200 символов") String title,

            String content,

            /** Порядок внутри раздела; {@code null} — оставить текущий (или 0). */
            Integer orderIndex,

            /**
             * Признак публикации. {@code null} при создании — черновик
             * (публикация отдельным действием), при правке — статус не
             * меняется.
             */
            Boolean published
    ) {
    }

    /** Статья глазами формы управления. */
    public record Article(
            Long id,
            String section,
            String sectionTitle,
            String title,
            String content,
            Integer orderIndex,
            boolean published,
            String statusTitle,
            LocalDateTime updatedAt,
            String updatedBy
    ) {
    }

    /** Раздел портала со счётчиком статей (для правой колонки экрана). */
    public record SectionSummary(String code, String title, long total, long drafts) {
    }

    /** Сводка управления: статьи, разделы и число черновиков. */
    public record ManageOverview(
            long totalArticles,
            long draftArticles,
            List<SectionSummary> sections,
            List<Article> articles
    ) {
    }

    /** Сборка строки статьи. */
    public static Article of(OnboardingArticle article) {
        return new Article(
                article.getId(),
                article.getSection().name(),
                article.getSection().getTitle(),
                article.getTitle(),
                article.getContent(),
                article.getOrderIndex(),
                article.isPublished(),
                article.isPublished() ? "Опубликована" : "Черновик",
                article.getUpdatedAt(),
                article.getUpdatedBy() != null ? article.getUpdatedBy().getFullName() : null);
    }
}
