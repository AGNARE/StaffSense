package com.staffsense.demo.controller;

import com.staffsense.demo.model.Project;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.ProjectRepository;
import com.staffsense.demo.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectController(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String listProjects(Model model, Authentication auth) {
        User current = userRepository.findByEmail(auth.getName()).orElseThrow();
        List<Project> projects = projectRepository.findAll();
        model.addAttribute("currentUser", current);
        model.addAttribute("projects", projects);
        return "projects/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public String showCreateForm(Model model, Authentication auth) {
        addFormReferenceData(model, auth);
        model.addAttribute("project", new Project());
        return "projects/new";
    }

    /**
     * Создание проекта (FR-007).
     *
     * <p>Ошибки валидации возвращают на форму с сообщением у поля и без
     * сохранения (ERR-006). Ранее при не выбранном владельце проект уходил в
     * {@code save()}, падал на {@code owner_pm_id NOT NULL} и пользователь
     * получал страницу 409 «Конфликт данных».
     */
    @PostMapping("/new")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public String createProject(@Valid @ModelAttribute("project") Project project,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttrs,
                                Authentication auth) {
        // @NotNull ловит «владелец не выбран», но не проверяет, что это
        // действительно активный PM: значение приходит из формы, поэтому
        // роль и активность проверяем отдельно.
        if (project.getOwnerPm() != null && !isActivePm(project.getOwnerPm())) {
            bindingResult.rejectValue("ownerPm", "ownerPm.notPm",
                    "Владельцем проекта может быть только активный PM.");
        }
        if (bindingResult.hasErrors()) {
            addFormReferenceData(model, auth);
            return "projects/new";
        }
        Project saved = projectRepository.save(project);
        redirectAttrs.addFlashAttribute("success", "Проект «" + saved.getName() + "» создан");
        return "redirect:/projects";
    }

    /**
     * Данные для формы. Атрибут {@code project} намеренно не трогаем: при
     * возврате на форму с ошибками в модели уже лежит связанный объект,
     * и перезапись потеряла бы введённые пользователем значения.
     */
    private void addFormReferenceData(Model model, Authentication auth) {
        model.addAttribute("currentUser", userRepository.findByEmail(auth.getName()).orElseThrow());
        model.addAttribute("pmList", userRepository.findAll().stream()
                .filter(ProjectController::isActivePm)
                .toList());
    }

    private static boolean isActivePm(User user) {
        return user.isActive() && user.getRole() == Role.PM;
    }
}
