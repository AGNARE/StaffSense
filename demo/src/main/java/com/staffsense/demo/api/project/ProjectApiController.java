package com.staffsense.demo.api.project;

import com.staffsense.demo.api.task.TaskDto;
import com.staffsense.demo.dto.task.TaskCreateRequest;
import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.Project;
import com.staffsense.demo.model.Task;
import com.staffsense.demo.model.TaskStatus;
import com.staffsense.demo.repository.ProjectRepository;
import com.staffsense.demo.repository.TaskRepository;
import com.staffsense.demo.service.task.TaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Проекты и их доски.
 *
 * <p>Чтение доступно всем авторизованным (как и серверные страницы), создание
 * задачи проверяется в {@link TaskService}: ADMIN — везде, PM — в своих
 * проектах, сотрудник — там, где участвует. Смена статуса остаётся на
 * существующем {@code POST /api/tasks/{id}/status} с проверкой переходов
 * по BR-014 — дублировать это правило вторым эндпоинтом было бы ошибкой.
 */
@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Проекты", description = "Список проектов, доска, создание задач")
public class ProjectApiController {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskService taskService;

    public ProjectApiController(ProjectRepository projectRepository,
                                TaskRepository taskRepository,
                                TaskService taskService) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.taskService = taskService;
    }

    /** Список проектов с прогрессом по задачам. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ProjectApiDtos.ProjectCard> list() {
        return projectRepository.findAll().stream()
                .sorted(Comparator.comparing(Project::getName))
                .map(project -> new ProjectApiDtos.ProjectCard(
                        project.getId(),
                        project.getName(),
                        project.getDescription(),
                        project.getOwnerPm() != null ? project.getOwnerPm().getFullName() : null,
                        project.getCreatedAt(),
                        counts(taskRepository.findByProjectId(project.getId()))))
                .toList();
    }

    /** Доска проекта: задачи в порядке статуса, затем по сроку. */
    @GetMapping("/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public ProjectApiDtos.Board board(@PathVariable Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Проект не найден: " + projectId));

        List<Task> tasks = taskRepository.findByProjectId(projectId);

        return new ProjectApiDtos.Board(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getOwnerPm() != null ? project.getOwnerPm().getFullName() : null,
                project.getCreatedAt(),
                counts(tasks),
                tasks.stream()
                        .sorted(Comparator
                                .comparingInt((Task task) -> task.getStatus().ordinal())
                                .thenComparing(task -> task.getDueDate() == null
                                        ? java.time.LocalDate.MAX : task.getDueDate()))
                        .map(TaskDto::from)
                        .toList());
    }

    /**
     * Создание задачи в проекте.
     *
     * <p>{@code projectId} берётся из пути, а не из тела: иначе клиент мог бы
     * прислать один проект в URL и другой в JSON.
     */
    @PostMapping("/{projectId}/tasks")
    @PreAuthorize("hasAnyRole('ADMIN','HR','PM','EXEC','EMPLOYEE')")
    public TaskDto createTask(@PathVariable Long projectId,
                              @Valid @RequestBody ProjectApiDtos.CreateTaskRequest request,
                              Authentication auth) {
        TaskCreateRequest taskRequest = TaskCreateRequest.builder()
                .title(request.title())
                .description(request.description())
                .projectId(projectId)
                .assigneeId(request.assigneeId())
                .priority(request.priority())
                .estimateHours(request.estimateHours())
                .dueDate(request.dueDate())
                .build();

        return TaskDto.from(taskService.createTask(taskRequest, auth));
    }

    private ProjectApiDtos.Counts counts(List<Task> tasks) {
        long todo = tasks.stream().filter(t -> t.getStatus() == TaskStatus.TODO).count();
        long inProgress = tasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
        long review = tasks.stream().filter(t -> t.getStatus() == TaskStatus.REVIEW).count();
        long done = tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        return ProjectApiDtos.Counts.of(todo, inProgress, review, done);
    }
}
