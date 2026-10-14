package com.staffsense.demo.controller;

import com.staffsense.demo.model.AiSkill;
import com.staffsense.demo.model.SkillRole;
import com.staffsense.demo.service.AiSkillCatalogService;
import com.staffsense.demo.service.TeamAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Управление каталогом ИИ-скиллов — только ADMIN (FR-033).
 *
 * <p>Каталог наполняет роль с правом администрирования: добавление, правка,
 * удаление, публикация и загрузка файла скилла.
 */
@Controller
@RequestMapping("/skills/manage")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AiSkillManageController {

    private final AiSkillCatalogService catalogService;
    private final TeamAccessService teamAccessService;

    @GetMapping
    public String list(Model model, Authentication auth) {
        model.addAttribute("currentUser", teamAccessService.requireCurrentUser(auth));
        model.addAttribute("skills", catalogService.allForManage());
        return "skills/manage";
    }

    @GetMapping("/new")
    public String createForm(Model model, Authentication auth) {
        model.addAttribute("currentUser", teamAccessService.requireCurrentUser(auth));
        model.addAttribute("skill", AiSkill.builder().published(true).build());
        model.addAttribute("roles", SkillRole.values());
        model.addAttribute("mode", "create");
        return "skills/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("skill") AiSkill skill,
                         BindingResult binding,
                         @RequestParam(value = "file", required = false) MultipartFile file,
                         Model model,
                         Authentication auth,
                         RedirectAttributes redirectAttrs) {
        if (binding.hasErrors()) {
            model.addAttribute("currentUser", teamAccessService.requireCurrentUser(auth));
            model.addAttribute("roles", SkillRole.values());
            model.addAttribute("mode", "create");
            return "skills/form";
        }
        catalogService.create(skill, file, teamAccessService.requireCurrentUser(auth));
        redirectAttrs.addFlashAttribute("success", "Скилл «" + skill.getName() + "» добавлен в каталог.");
        return "redirect:/skills/manage";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, Authentication auth) {
        model.addAttribute("currentUser", teamAccessService.requireCurrentUser(auth));
        model.addAttribute("skill", catalogService.get(id));
        model.addAttribute("roles", SkillRole.values());
        model.addAttribute("mode", "edit");
        return "skills/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("skill") AiSkill skill,
                         BindingResult binding,
                         @RequestParam(value = "file", required = false) MultipartFile file,
                         Model model,
                         Authentication auth,
                         RedirectAttributes redirectAttrs) {
        if (binding.hasErrors()) {
            model.addAttribute("currentUser", teamAccessService.requireCurrentUser(auth));
            model.addAttribute("roles", SkillRole.values());
            model.addAttribute("mode", "edit");
            return "skills/form";
        }
        catalogService.update(id, skill, file, teamAccessService.requireCurrentUser(auth));
        redirectAttrs.addFlashAttribute("success", "Скилл «" + skill.getName() + "» обновлён.");
        return "redirect:/skills/manage";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        catalogService.delete(id);
        redirectAttrs.addFlashAttribute("info", "Скилл удалён из каталога.");
        return "redirect:/skills/manage";
    }
}
