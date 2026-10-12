package com.staffsense.demo.service;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmployeeService {

    private final UserRepository userRepository;

    public EmployeeService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> findAllActive() {
        return userRepository.findByActive(true);
    }

    public Page<User> findAllActive(Pageable pageable) {
        return userRepository.findByActivePaged(true, pageable);
    }

    public Page<User> findByDepartment(String department, Pageable pageable) {
        return userRepository.findByDepartmentAndActivePaged(department, true, pageable);
    }

    // ── Ограничение «своей командой» для роли PM ─────────────────────────

    /** Активные сотрудники в подчинении менеджера (manager_id). */
    public Page<User> findTeamOf(Long managerId, Pageable pageable) {
        return userRepository.findByManagerAndActivePaged(managerId, true, pageable);
    }

    /** То же с фильтром по подразделению. */
    public Page<User> findTeamOfByDepartment(Long managerId, String department, Pageable pageable) {
        return userRepository.findByManagerAndDepartmentAndActivePaged(managerId, department, true, pageable);
    }

    /** Подразделения, представленные в команде менеджера. */
    public List<String> getDepartmentsOfTeam(Long managerId) {
        return userRepository.findActiveByManager(managerId, true).stream()
                .map(User::getDepartment)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Сотрудник по ID. Ранее бросался IllegalArgumentException, из-за чего
     * отсутствующий сотрудник давал 400 вместо 404.
     */
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
    }

    public List<User> findByDepartment(String department) {
        return userRepository.findByDepartmentAndActive(department, true);
    }

    public List<String> getAllDepartments() {
        return userRepository.findByActive(true).stream()
                .map(User::getDepartment)
                .distinct()
                .sorted()
                .toList();
    }

    public double getAverageSalaryForGrade(GradeLevel grade) {
        return userRepository.findByGradeLevelAndActive(grade, true).stream()
                .mapToDouble(u -> u.getSalary().doubleValue())
                .average()
                .orElse(0.0);
    }
}
