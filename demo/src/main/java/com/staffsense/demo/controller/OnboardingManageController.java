package com.staffsense.demo.controller;

import com.staffsense.demo.model.OnboardingArticle;
import com.staffsense.demo.model.OnboardingSection;
import com.staffsense.demo.service.OnboardingService;
import com.staffsense.demo.service.TeamAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Управление содержимым портала онбординга — только HR и ADMIN.
 *
 * <p>Иначе любой сотрудник смог бы переписать должностные инструкции и регламенты.
 */
@Controller
@RequestMapping("/onboarding/manage")
@PreAuthorize("hasAnyRole('ADMIN','HR')")
@RequiredArgsConstructor
public class OnboardingManageController {

    private final OnboardingService onboardingService;
    private final TeamAccessService teamAccessService;

    @GetMapping
    public String list(Model model, Authentication auth) {
        model.addAttribute("currentUser", teamAccessService.requireCurrentUser(auth));
        model.addAttribute("articles", onboardingService.getAllArticles());
        model.addAttribute("sections", OnboardingSection.values());
        return "onboarding/manage";
    }

    @GetMapping("/new")
    public String createForm(Model model, Authentication auth) {
        model.addAttribute("currentUser", teamAccessService.requireCurrentUser(auth));
        model.addAttribute("article", OnboardingArticle.builder().published(true).orderIndex(0).build());
        model.addAttribute("sections", OnboardingSection.values());
        model.addAttribute("mode", "create");
        return "onboarding/form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("article") OnboardingArticle article,
                         Authentication auth,
                         RedirectAttributes redirectAttrs) {
        onboardingService.save(article, teamAccessService.requireCurrentUser(auth));
        redirectAttrs.addFlashAttribute("success", "Статья «" + article.getTitle() + "» создана.");
        return "redirect:/onboarding/manage";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, Authentication auth) {
        model.addAttribute("currentUser", teamAccessService.requireCurrentUser(auth));
        model.addAttribute("article", onboardingService.getArticle(id));
        model.addAttribute("sections", OnboardingSection.values());
        model.addAttribute("mode", "edit");
        return "onboarding/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @ModelAttribute("article") OnboardingArticle article,
                         Authentication auth,
                         RedirectAttributes redirectAttrs) {
        onboardingService.update(id, article, teamAccessService.requireCurrentUser(auth));
        redirectAttrs.addFlashAttribute("success", "Статья «" + article.getTitle() + "» обновлена.");
        return "redirect:/onboarding/manage";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        onboardingService.delete(id);
        redirectAttrs.addFlashAttribute("info", "Статья удалена.");
        return "redirect:/onboarding/manage";
    }
}
