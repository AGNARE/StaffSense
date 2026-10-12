package com.staffsense.demo.service;

import com.staffsense.demo.model.OneOnOneMeeting;
import com.staffsense.demo.model.Project;
import com.staffsense.demo.model.Task;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Проверки прав уровня данных («своя команда», «своя задача», «своя встреча»).
 *
 * <p>До этого класса в проекте не было ни одной проверки принадлежности:
 * PM видел данные всех сотрудников, EMPLOYEE мог менять любую задачу,
 * а завершить/отменить можно было чужую встречу 1:1 (IDOR).
 *
 * <p>Правило «своей команды»: сотрудник считается подчинённым менеджера,
 * если {@code employee.manager.id == manager.id} (поле users.manager_id).
 */
@Service
@RequiredArgsConstructor
public class TeamAccessService {

    private final UserRepository userRepository;

    /** Текущий пользователь или AccessDeniedException. */
    public User requireCurrentUser(Authentication auth) {
        User user = currentUserOrNull(auth);
        if (user == null) {
            throw new AccessDeniedException("Пользователь не аутентифицирован");
        }
        return user;
    }

    /** Текущий пользователь или null (без исключения). */
    public User currentUserOrNull(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    public boolean isEmployeeInTeamOf(User manager, User employee) {
        if (manager == null || employee == null || manager.getId() == null) {
            return false;
        }
        return employee.getManager() != null
                && employee.getManager().getId() != null
                && employee.getManager().getId().equals(manager.getId());
    }

    /**
     * Просмотр карточки сотрудника: ADMIN/HR/EXEC — все, PM — только своя команда,
     * EMPLOYEE — только себя.
     */
    public boolean canViewEmployee(User actor, User target) {
        if (actor == null || target == null) {
            return false;
        }
        if (actor.getId() != null && actor.getId().equals(target.getId())) {
            return true;
        }
        return switch (actor.getRole()) {
            case ADMIN, HR, EXEC -> true;
            case PM -> isEmployeeInTeamOf(actor, target);
            case EMPLOYEE -> false;
        };
    }

    public void requireCanViewEmployee(User actor, User target) {
        if (!canViewEmployee(actor, target)) {
            throw new AccessDeniedException("Нет доступа к данным сотрудника ID=" + target.getId());
        }
    }

    /**
     * Изменение задачи: ADMIN — любую; PM — если он владелец проекта или
     * руководитель исполнителя; EMPLOYEE — только свои задачи.
     * HR и EXEC изменения задач не выполняют (только чтение).
     */
    public boolean canModifyTask(User actor, Task task) {
        if (actor == null || task == null) {
            return false;
        }
        return switch (actor.getRole()) {
            case ADMIN -> true;
            case PM -> isProjectOwner(actor, task.getProject())
                    || isEmployeeInTeamOf(actor, task.getAssignee());
            case EMPLOYEE -> task.getAssignee() != null
                    && actor.getId() != null
                    && actor.getId().equals(task.getAssignee().getId());
            case HR, EXEC -> false;
        };
    }

    public void requireCanModifyTask(User actor, Task task) {
        if (!canModifyTask(actor, task)) {
            throw new AccessDeniedException("Нет прав на изменение задачи ID=" + task.getId());
        }
    }

    /** Создание задачи в проекте: ADMIN — везде, PM — только в своих проектах. */
    public boolean canCreateTaskIn(User actor, Project project) {
        if (actor == null || project == null) {
            return false;
        }
        return switch (actor.getRole()) {
            case ADMIN -> true;
            case PM -> isProjectOwner(actor, project);
            default -> false;
        };
    }

    public void requireCanCreateTaskIn(User actor, Project project) {
        if (!canCreateTaskIn(actor, project)) {
            throw new AccessDeniedException("Нет прав на создание задачи в проекте ID=" + project.getId());
        }
    }

    /** Изменение встречи 1:1: ADMIN либо менеджер этой встречи (PM/HR). */
    public boolean canManageMeeting(User actor, OneOnOneMeeting meeting) {
        if (actor == null || meeting == null) {
            return false;
        }
        return switch (actor.getRole()) {
            case ADMIN -> true;
            case PM, HR -> meeting.getManager() != null
                    && actor.getId() != null
                    && actor.getId().equals(meeting.getManager().getId());
            case EMPLOYEE, EXEC -> false;
        };
    }

    public void requireCanManageMeeting(User actor, OneOnOneMeeting meeting) {
        if (!canManageMeeting(actor, meeting)) {
            throw new AccessDeniedException("Нет прав на изменение встречи ID=" + meeting.getId());
        }
    }

    /**
     * Назначение встречи сотруднику: ADMIN/HR — любому, PM — только своему подчинённому.
     * Дополнительно запрещено создавать встречу «с самим собой».
     */
    public boolean canScheduleMeetingWith(User actor, User employee) {
        if (actor == null || employee == null) {
            return false;
        }
        if (actor.getId() != null && actor.getId().equals(employee.getId())) {
            return false;
        }
        return switch (actor.getRole()) {
            case ADMIN, HR -> true;
            case PM -> isEmployeeInTeamOf(actor, employee);
            default -> false;
        };
    }

    private boolean isProjectOwner(User actor, Project project) {
        return project != null
                && project.getOwnerPm() != null
                && actor.getId() != null
                && actor.getId().equals(project.getOwnerPm().getId());
    }
}
