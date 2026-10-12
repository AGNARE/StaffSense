package com.staffsense.demo.controller.task;

import com.staffsense.demo.dto.task.TaskCreateRequest;
import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.Project;
import com.staffsense.demo.model.Task;
import com.staffsense.demo.model.TaskStatus;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.ProjectRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.task.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ProjectBoardController {

    private final TaskService taskService;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    /** Доска проекта — доступна всем аутентифицированным (проекты: просмотр — все роли). */
    @GetMapping("/projects/{id}/board")
    public String getProjectBoard(@PathVariable Long id, Model model, Authentication auth) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with ID: " + id));
        List<Task> tasks = taskService.getTasksByProject(id);

        // Pre-compute tasks grouped by status to avoid stream() in Thymeleaf
        Map<String, List<Task>> tasksByStatus = Arrays.stream(TaskStatus.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        status -> tasks.stream()
                                .filter(t -> t.getStatus() == status)
                                .collect(Collectors.toList())
                ));

        model.addAttribute("currentUser", userRepository.findByEmail(auth.getName()).orElseThrow());
        model.addAttribute("project", project);
        model.addAttribute("tasks", tasks);
        model.addAttribute("tasksByStatus", tasksByStatus);

        return "projects/board";
    }

    /** Форма создания задачи — только ADMIN и PM (создание задач: PM/ADMIN). */
    @GetMapping("/projects/{id}/tasks/new")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public String showCreateTaskForm(@PathVariable Long id, Model model, Authentication auth) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with ID: " + id));

        model.addAttribute("currentUser", userRepository.findByEmail(auth.getName()).orElseThrow());
        model.addAttribute("project", project);
        model.addAttribute("employees", userRepository.findAll().stream()
                .filter(User::isActive).toList());
        model.addAttribute("request", new TaskCreateRequest());
        return "projects/task-form";
    }

    /** Создание задачи из формы — только ADMIN и PM. */
    @PostMapping("/projects/{id}/tasks/new")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public String createTask(@PathVariable Long id,
                             @Valid @ModelAttribute("request") TaskCreateRequest request,
                             BindingResult result,
                             Model model,
                             Authentication auth,
                             RedirectAttributes redirectAttrs) {
        request.setProjectId(id);

        if (result.hasErrors()) {
            model.addAttribute("currentUser", userRepository.findByEmail(auth.getName()).orElseThrow());
            model.addAttribute("project", projectRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Project not found")));
            model.addAttribute("employees", userRepository.findAll().stream()
                    .filter(User::isActive).toList());
            return "projects/task-form";
        }

        Task task = taskService.createTask(request, auth);
        redirectAttrs.addFlashAttribute("success", "Задача «" + task.getTitle() + "» создана");
        return "redirect:/projects/" + id + "/board";
    }
}
