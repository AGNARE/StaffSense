package com.staffsense.demo.service.task;

import com.staffsense.demo.exception.InvalidStatusTransitionException;
import com.staffsense.demo.model.Project;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.Task;
import com.staffsense.demo.model.TaskStatus;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.ProjectRepository;
import com.staffsense.demo.repository.TaskRepository;
import com.staffsense.demo.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FR-010: допустимые переходы статуса задачи.
 *
 * <p>До этой проверки задачу можно было перескочить из TODO сразу в DONE или
 * вернуть готовую в проверку, минуя маршрут. Это ломало completedAt и
 * reopenedCount, а они питают расчёт KPI (BR-009).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskStatusTransitionTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    // ── матрица переходов ────────────────────────────────────────────────────

    @Test
    @DisplayName("Маршрут задачи: TODO → IN_PROGRESS → REVIEW → DONE разрешён")
    void forwardRouteIsAllowed() {
        assertTrue(TaskStatus.TODO.canTransitionTo(TaskStatus.IN_PROGRESS));
        assertTrue(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.REVIEW));
        assertTrue(TaskStatus.REVIEW.canTransitionTo(TaskStatus.DONE));
    }

    @Test
    @DisplayName("Шаг назад разрешён: из проверки в работу, из работы в очередь")
    void stepBackIsAllowed() {
        assertTrue(TaskStatus.REVIEW.canTransitionTo(TaskStatus.IN_PROGRESS));
        assertTrue(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.TODO));
    }

    @Test
    @DisplayName("Из DONE можно только переоткрыть задачу — в работу (BR-014)")
    void reopeningIsAllowedOnlyToInProgress() {
        assertTrue(TaskStatus.DONE.canTransitionTo(TaskStatus.IN_PROGRESS));
        assertFalse(TaskStatus.DONE.canTransitionTo(TaskStatus.REVIEW));
        assertFalse(TaskStatus.DONE.canTransitionTo(TaskStatus.TODO));
    }

    @Test
    @DisplayName("Перескок через этап запрещён: TODO → DONE, TODO → REVIEW, REVIEW → TODO")
    void skippingStagesIsForbidden() {
        assertFalse(TaskStatus.TODO.canTransitionTo(TaskStatus.DONE));
        assertFalse(TaskStatus.TODO.canTransitionTo(TaskStatus.REVIEW));
        assertFalse(TaskStatus.REVIEW.canTransitionTo(TaskStatus.TODO));
    }

    @Test
    @DisplayName("Повторная установка того же статуса не считается переходом")
    void sameStatusIsAllowed() {
        for (TaskStatus s : TaskStatus.values()) {
            assertTrue(s.canTransitionTo(s), "статус " + s + " сам в себя");
        }
    }

    // ── проверка на уровне сервиса ───────────────────────────────────────────

    @Test
    @DisplayName("Сервис отклоняет недопустимый переход и не меняет задачу")
    void serviceRejectsInvalidTransition() {
        User admin = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .findFirst().orElseThrow();
        Project project = projectRepository.findAll().stream().findFirst().orElseThrow();

        Task task = taskRepository.save(Task.builder()
                .project(project)
                .title("Задача для проверки переходов")
                .status(TaskStatus.TODO)
                .reopenedCount(0)
                .build());

        Authentication auth = new UsernamePasswordAuthenticationToken(
                admin.getEmail(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        InvalidStatusTransitionException ex = assertThrows(
                InvalidStatusTransitionException.class,
                () -> taskService.updateStatus(task.getId(), TaskStatus.DONE, auth));

        assertTrue(ex.getMessage().contains("Недопустимый переход"),
                "сообщение объясняет причину: " + ex.getMessage());

        Task after = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.TODO, after.getStatus(), "статус не изменился");
        assertNull(after.getCompletedAt(), "дата завершения не проставлена");
    }

    @Test
    @DisplayName("Допустимый переход выполняется и фиксирует завершение")
    void validTransitionStillWorks() {
        User admin = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .findFirst().orElseThrow();
        Project project = projectRepository.findAll().stream().findFirst().orElseThrow();

        Task task = taskRepository.save(Task.builder()
                .project(project)
                .title("Задача для допустимого перехода")
                .status(TaskStatus.REVIEW)
                .reopenedCount(0)
                .build());

        Authentication auth = new UsernamePasswordAuthenticationToken(
                admin.getEmail(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        Task updated = taskService.updateStatus(task.getId(), TaskStatus.DONE, auth);

        assertEquals(TaskStatus.DONE, updated.getStatus());
        assertNotNull(updated.getCompletedAt(), "дата завершения проставлена (BR-014)");
    }
}
