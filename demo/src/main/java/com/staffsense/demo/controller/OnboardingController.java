package com.staffsense.demo.controller;

import com.staffsense.demo.dto.onboarding.ChecklistItem;
import com.staffsense.demo.model.OnboardingArticle;
import com.staffsense.demo.model.OnboardingSection;
import com.staffsense.demo.model.User;
import com.staffsense.demo.service.OnboardingService;
import com.staffsense.demo.service.TeamAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Внутренний портал онбординга (модуль B) — чтение.
 *
 * <p>Доступен любому аутентифицированному сотруднику: это внутренний справочник
 * компании (ценности, структура, инструкции, регламенты, FAQ, чек-лист).
 */
@Controller
@RequestMapping("/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final TeamAccessService teamAccessService;

    /** Главная портала: разделы и последние статьи. */
    @GetMapping
    public String index(Model model, Authentication auth) {
        User currentUser = teamAccessService.requireCurrentUser(auth);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("sections", onboardingService.getSectionSummaries(false));
        model.addAttribute("latestArticles", onboardingService.getLatestArticles(6));
        model.addAttribute("progress", onboardingService.getChecklistProgress(currentUser));
        return "onboarding/index";
    }

    /** Статьи выбранного раздела. */
    @GetMapping("/section/{section}")
    public String section(@PathVariable OnboardingSection section, Model model, Authentication auth) {
        List<OnboardingArticle> articles = onboardingService.getArticles(section, false);
        model.addAttribute("currentUser", teamAccessService.requireCurrentUser(auth));
        model.addAttribute("section", section);
        model.addAttribute("articles", articles);
        model.addAttribute("sections", onboardingService.getSectionSummaries(false));
        return "onboarding/section";
    }

    /** Просмотр статьи. */
    @GetMapping("/article/{id}")
    public String article(@PathVariable Long id, Model model, Authentication auth) {
        OnboardingArticle article = onboardingService.getArticle(id);
        model.addAttribute("currentUser", teamAccessService.requireCurrentUser(auth));
        model.addAttribute("article", article);
        model.addAttribute("sections", onboardingService.getSectionSummaries(false));
        return "onboarding/article";
    }

    /** Чек-лист новичка с персональным прогрессом. */
    @GetMapping("/checklist")
    public String checklist(Model model, Authentication auth) {
        User currentUser = teamAccessService.requireCurrentUser(auth);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("items", onboardingService.getChecklist(currentUser));
        model.addAttribute("progress", onboardingService.getChecklistProgress(currentUser));
        model.addAttribute("sections", onboardingService.getSectionSummaries(false));
        return "onboarding/checklist";
    }

    /** Отметить/снять пункт чек-листа. */
    @PostMapping("/checklist/{articleId}/toggle")
    public String toggleChecklist(@PathVariable Long articleId,
                                  Authentication auth,
                                  RedirectAttributes redirectAttrs) {
        User currentUser = teamAccessService.requireCurrentUser(auth);
        boolean completed = onboardingService.toggleChecklistItem(currentUser, articleId);
        redirectAttrs.addFlashAttribute("info",
                completed ? "Пункт отмечен как выполненный." : "Отметка снята.");
        return "redirect:/onboarding/checklist";
    }
}
