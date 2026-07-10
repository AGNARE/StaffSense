package com.staffsense.demo.controller;

import com.staffsense.demo.model.Project;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.ProjectRepository;
import com.staffsense.demo.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
        User current = userRepository.findByEmail(auth.getName()).orElseThrow();
        model.addAttribute("currentUser", current);
        model.addAttribute("project", new Project());
        model.addAttribute("pmList", userRepository.findAll().stream()
                .filter(u -> u.isActive() && u.getRole() == com.staffsense.demo.model.Role.PM)
                .toList());
        return "projects/new";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public String createProject(@ModelAttribute Project project,
                                @RequestParam(required = false) Long ownerPmId,
                                RedirectAttributes redirectAttrs,
                                Authentication auth) {
        if (ownerPmId != null) {
            User pm = userRepository.findById(ownerPmId).orElse(null);
            project.setOwnerPm(pm);
        }
        Project saved = projectRepository.save(project);
        redirectAttrs.addFlashAttribute("success", "Проект «" + saved.getName() + "» создан");
        return "redirect:/projects";
    }
}
