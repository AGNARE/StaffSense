package com.staffsense.demo.repository;

import com.staffsense.demo.model.OnboardingArticle;
import com.staffsense.demo.model.OnboardingChecklistProgress;
import com.staffsense.demo.model.OnboardingSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OnboardingChecklistProgressRepository
        extends JpaRepository<OnboardingChecklistProgress, Long> {

    List<OnboardingChecklistProgress> findByUserId(Long userId);

    Optional<OnboardingChecklistProgress> findByUserIdAndArticleId(Long userId, Long articleId);

    long countByUserId(Long userId);

    /**
     * Отметки только по актуальным пунктам чек-листа.
     *
     * <p>{@code countByUserId} считает отметки по всем разделам: если статью
     * перенесли из чек-листа в другой раздел, прогресс превышал 100 %.
     */
    long countByUserIdAndArticleSectionAndArticlePublishedTrue(Long userId, OnboardingSection section);

    void deleteByArticleId(Long articleId);

    void deleteByArticle(OnboardingArticle article);
}
