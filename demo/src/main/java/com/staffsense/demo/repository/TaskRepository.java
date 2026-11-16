package com.staffsense.demo.repository;

import com.staffsense.demo.model.Task;
import com.staffsense.demo.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByProjectId(Long projectId);
    List<Task> findByAssigneeId(Long assigneeId);
    List<Task> findByAssigneeIdAndStatusNot(Long assigneeId, TaskStatus status);

    /**
     * Задачи исполнителя в заданном статусе.
     * Добавлено, чтобы расчёт KPI не выгружал всю таблицу задач (findAll())
     * и не фильтровал её в памяти для каждого сотрудника.
     */
    List<Task> findByAssigneeIdAndStatus(Long assigneeId, TaskStatus status);

    /**
     * Есть ли у сотрудника задача в проекте, которым владеет этот PM.
     *
     * <p>Нужно правилу «своей команды»: PM видит не только прямых подчинённых
     * (users.manager_id), но и исполнителей своих проектов. Проверка идёт
     * одним запросом на пару «владелец проекта — исполнитель», без выгрузки
     * задач в память.
     */
    boolean existsByProjectOwnerPmIdAndAssigneeId(Long ownerPmId, Long assigneeId);
}
