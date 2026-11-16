package com.staffsense.demo;

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
import com.staffsense.demo.service.TeamAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Границы «своей команды» PM (решение владельца — вариант C).
 *
 * <p>Команда PM — это не только прямые подчинённые по users.manager_id,
 * но и исполнители задач в проектах, которыми PM владеет. Без второго условия
 * сотрудник, работающий в проекте PM, но подчинённый другому руководителю,
 * не был виден в «Кадрах» и не попадал в риски.
 *
 * <p>Тест фиксирует обе половины правила и границу: чужая команда по-прежнему
 * закрыта, а ADMIN/HR/EXEC по-прежнему видят всех.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TeamScopeTest {

    @Autowired
    private TeamAccessService teamAccessService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    @DisplayName("(а) прямой подчинённый виден PM")
    void subordinateIsVisible() {
        User pm = user("teamscope.pm.sub@test.local", Role.PM, null);
        User subordinate = user("teamscope.emp.sub@test.local", Role.EMPLOYEE, pm);

        assertThat(teamAccessService.isEmployeeInTeamOf(pm, subordinate))
                .as("подчинённый (users.manager_id) обязан быть в команде PM")
                .isTrue();
        assertThat(teamAccessService.canViewEmployee(pm, subordinate))
                .as("PM должен открывать карточку подчинённого")
                .isTrue();
    }

    @Test
    @DisplayName("(б) исполнитель проекта PM виден, даже если он ему не подчиняется")
    void projectMemberIsVisible() {
        User pm = user("teamscope.pm.member@test.local", Role.PM, null);
        User otherPm = user("teamscope.pm2.member@test.local", Role.PM, null);
        // Формально сотрудник подчинён другому руководителю — команда PM
        // расширяется только за счёт участия в его проекте.
        User member = user("teamscope.emp.member@test.local", Role.EMPLOYEE, otherPm);

        Project project = project("TeamScope Project (member)", pm);
        task(project, member, "Задача участнику проекта");

        assertThat(member.getManager().getId())
                .as("условие теста: сотрудник не подчинён этому PM")
                .isNotEqualTo(pm.getId());
        assertThat(teamAccessService.isEmployeeInTeamOf(pm, member))
                .as("исполнитель задачи в проекте PM обязан быть в его команде")
                .isTrue();
        assertThat(teamAccessService.canViewEmployee(pm, member))
                .as("PM должен открывать карточку участника своего проекта")
                .isTrue();
    }

    @Test
    @DisplayName("(в) сотрудник чужого проекта PM не виден")
    void foreignProjectMemberIsNotVisible() {
        User pm = user("teamscope.pm.foreign@test.local", Role.PM, null);
        User otherPm = user("teamscope.pm2.foreign@test.local", Role.PM, null);
        User stranger = user("teamscope.emp.foreign@test.local", Role.EMPLOYEE, otherPm);
        User hr = user("teamscope.hr.foreign@test.local", Role.HR, null);

        Project foreign = project("TeamScope Project (foreign)", otherPm);
        Task foreignTask = task(foreign, stranger, "Задача в чужом проекте");

        assertThat(teamAccessService.isEmployeeInTeamOf(pm, stranger))
                .as("участник чужого проекта не должен попадать в команду PM")
                .isFalse();
        assertThat(teamAccessService.canViewEmployee(pm, stranger))
                .as("PM не должен открывать карточку сотрудника чужой команды")
                .isFalse();
        assertThat(teamAccessService.canModifyTask(pm, foreignTask))
                .as("расширение команды не даёт PM права менять задачи чужого проекта")
                .isFalse();

        // Правило «ADMIN/HR/EXEC видят всех» не изменилось.
        assertThat(teamAccessService.canViewEmployee(hr, stranger))
                .as("HR по-прежнему видит любого сотрудника")
                .isTrue();
    }

    private User user(String email, Role role, User manager) {
        return userRepository.save(User.builder()
                .fullName("Проверка Команды " + role.name())
                .email(email)
                .passwordHash("hash")
                .role(role)
                .department("Product")
                .position("Specialist")
                .salary(new BigDecimal("70000"))
                .gradeLevel(GradeLevel.MIDDLE)
                .weeklyCapacityHours(40)
                .hireDate(LocalDate.of(2024, 2, 1))
                .manager(manager)
                .active(true)
                .build());
    }

    private Project project(String name, User owner) {
        return projectRepository.save(Project.builder()
                .name(name)
                .description("Проект для проверки границ команды PM")
                .ownerPm(owner)
                .build());
    }

    private Task task(Project project, User assignee, String title) {
        return taskRepository.save(Task.builder()
                .project(project)
                .title(title)
                .assignee(assignee)
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .estimateHours(8.0)
                .build());
    }
}
