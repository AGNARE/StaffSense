package com.staffsense.demo.api.project;

import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.Project;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.Task;
import com.staffsense.demo.model.TaskPriority;
import com.staffsense.demo.model.TaskStatus;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.ProjectRepository;
import com.staffsense.demo.repository.TaskRepository;
import com.staffsense.demo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Контракт {@code /api/v1/projects}.
 *
 * <p>Проверяем три вещи, на которые опирается доска: список с прогрессом,
 * задачи с разрешёнными переходами (правило BR-014 приходит с сервера,
 * а не дублируется в клиенте) и создание задачи во вложенном маршруте.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    private Project project;
    private User admin;

    @BeforeEach
    void setUp() {
        admin = userRepository.findByEmail("proj.admin@test.local").orElseGet(
                () -> userRepository.save(User.builder()
                        .fullName("Админ Проектов")
                        .email("proj.admin@test.local")
                        .passwordHash("hash")
                        .role(Role.ADMIN)
                        .department("PMO")
                        .position("Admin")
                        .salary(new BigDecimal("120000"))
                        .gradeLevel(GradeLevel.SENIOR)
                        .weeklyCapacityHours(40)
                        .hireDate(LocalDate.of(2022, 1, 1))
                        .active(true)
                        .build()));

        project = projectRepository.save(Project.builder()
                .name("Тестовый проект API")
                .description("Для проверки контракта")
                .ownerPm(admin)
                .createdAt(LocalDateTime.now())
                .build());

        taskRepository.save(Task.builder()
                .project(project)
                .title("Задача в очереди")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .reopenedCount(0)
                .build());

        taskRepository.save(Task.builder()
                .project(project)
                .title("Задача сделана")
                .status(TaskStatus.DONE)
                .priority(TaskPriority.HIGH)
                .reopenedCount(0)
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Список проектов содержит прогресс по задачам")
    void listContainsProgress() throws Exception {
        authenticate("proj.admin@test.local", "ROLE_ADMIN");

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(hasItem("Тестовый проект API")));
    }

    @Test
    @DisplayName("Доска отдаёт задачи с разрешёнными переходами")
    void boardReturnsAllowedTransitions() throws Exception {
        authenticate("proj.admin@test.local", "ROLE_ADMIN");

        mockMvc.perform(get("/api/v1/projects/" + project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[*].title").value(hasItem("Задача в очереди")))
                // из TODO по BR-014 можно только в работу
                .andExpect(jsonPath("$.tasks[?(@.title=='Задача в очереди')].allowedTransitions[*]")
                        .value(hasItem("IN_PROGRESS")))
                // и ровно один вариант, без самоперехода
                .andExpect(jsonPath("$.tasks[?(@.title=='Задача в очереди')].allowedTransitions.length()")
                        .value(1));
    }

    @Test
    @DisplayName("Задача создаётся во вложенном маршруте, проект берётся из URL")
    void createsTaskInProject() throws Exception {
        authenticate("proj.admin@test.local", "ROLE_ADMIN");

        mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Новая задача из API\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Новая задача из API"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.projectId").value(project.getId()));
    }

    @Test
    @DisplayName("Пустое название задачи отклоняется с 400")
    void rejectsEmptyTitle() throws Exception {
        authenticate("proj.admin@test.local", "ROLE_ADMIN");

        mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private void authenticate(String email, String authority) {
        var token = new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
