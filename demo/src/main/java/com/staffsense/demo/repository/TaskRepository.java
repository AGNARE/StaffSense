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
}
