package com.staffsense.demo.repository;

import com.staffsense.demo.model.OnboardingArticle;
import com.staffsense.demo.model.OnboardingSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OnboardingArticleRepository extends JpaRepository<OnboardingArticle, Long> {

    List<OnboardingArticle> findByPublishedTrueOrderBySectionAscOrderIndexAsc();

    List<OnboardingArticle> findBySectionAndPublishedTrueOrderByOrderIndexAsc(OnboardingSection section);

    List<OnboardingArticle> findBySectionOrderByOrderIndexAsc(OnboardingSection section);

    List<OnboardingArticle> findAllByOrderBySectionAscOrderIndexAsc();

    long countBySectionAndPublishedTrue(OnboardingSection section);

    long countBySection(OnboardingSection section);
}
