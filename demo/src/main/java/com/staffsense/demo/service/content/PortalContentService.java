package com.staffsense.demo.service.content;

import com.staffsense.demo.api.onboarding.PortalManageApiDtos;
import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.OnboardingArticle;
import com.staffsense.demo.model.OnboardingSection;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.OnboardingArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Управление содержимым портала: создание, правка и публикация статей.
 *
 * <p>Почему отдельный сервис, а не {@code OnboardingService}: сервис портала
 * обслуживает чтение и чек-лист сотрудника, и его методы {@code save/update}
 * принимают готовую сущность из серверной формы (вместе с флагом публикации).
 * Форме управления нужен другой контракт — «менять только переданные поля»,
 * иначе правка заголовка снимала бы статью с публикации. Общим остаётся
 * репозиторий, поэтому поведение портала сотрудника не меняется.
 */
@Service
public class PortalContentService {

    private final OnboardingArticleRepository articleRepository;

    public PortalContentService(OnboardingArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    /** Все статьи в порядке «раздел, затем порядок внутри раздела». */
    public List<OnboardingArticle> all() {
        return articleRepository.findAllByOrderBySectionAscOrderIndexAsc();
    }

    /** Статья по идентификатору или 404. */
    public OnboardingArticle require(Long id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Статья не найдена: " + id));
    }

    /**
     * Создание статьи.
     *
     * <p>Без явного {@code published} статья создаётся черновиком: экран
     * управления двухшаговый — «создать → проверить → опубликовать», и
     * случайная публикация черновика сотрудникам хуже лишнего клика.
     * Серверная форма портала при этом не меняется: она передаёт флаг сама.
     */
    @Transactional
    public OnboardingArticle create(PortalManageApiDtos.SaveRequest request, User actor) {
        OnboardingArticle article = OnboardingArticle.builder()
                .section(request.section())
                .title(request.title().trim())
                .content(request.content())
                .orderIndex(request.orderIndex() == null ? 0 : request.orderIndex())
                .published(request.published() != null && request.published())
                .updatedBy(actor)
                .updatedAt(LocalDateTime.now())
                .build();
        return articleRepository.save(article);
    }

    /** Правка статьи: меняются только переданные поля. */
    @Transactional
    public OnboardingArticle update(Long id, PortalManageApiDtos.SaveRequest request, User actor) {
        OnboardingArticle article = require(id);

        article.setSection(request.section());
        article.setTitle(request.title().trim());
        article.setContent(request.content());
        if (request.orderIndex() != null) {
            article.setOrderIndex(request.orderIndex());
        }
        if (request.published() != null) {
            article.setPublished(request.published());
        }
        article.setUpdatedBy(actor);
        article.setUpdatedAt(LocalDateTime.now());
        return articleRepository.save(article);
    }

    /**
     * Публикация статьи.
     *
     * <p>Идемпотентна: повторное нажатие «Опубликовать» на уже опубликованной
     * статье — не ошибка и не меняет дату обновления.
     */
    @Transactional
    public OnboardingArticle publish(Long id, User actor) {
        OnboardingArticle article = require(id);
        if (article.isPublished()) {
            return article;
        }
        article.setPublished(true);
        article.setUpdatedBy(actor);
        article.setUpdatedAt(LocalDateTime.now());
        return articleRepository.save(article);
    }

    /**
     * Счётчики по разделам: всего статей и черновиков.
     *
     * <p>Один запрос на все разделы, а не по запросу на раздел: счётчики
     * нужны всем шести разделам сразу, и шесть одинаковых выборок в одном
     * обработчике — лишняя нагрузка на пустом месте.
     */
    public List<PortalManageApiDtos.SectionSummary> sections() {
        List<OnboardingArticle> articles = all();
        return java.util.Arrays.stream(OnboardingSection.values())
                .map(section -> {
                    List<OnboardingArticle> inSection = articles.stream()
                            .filter(article -> article.getSection() == section)
                            .toList();
                    long drafts = inSection.stream().filter(article -> !article.isPublished()).count();
                    return new PortalManageApiDtos.SectionSummary(
                            section.name(), section.getTitle(), inSection.size(), drafts);
                })
                .toList();
    }
}
