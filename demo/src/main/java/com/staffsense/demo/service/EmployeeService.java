package com.staffsense.demo.service;

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

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + id));
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

    public double getAverageSalaryForGrade(com.staffsense.demo.model.GradeLevel grade) {
        return userRepository.findByGradeLevelAndActive(grade, true).stream()
                .mapToDouble(u -> u.getSalary().doubleValue())
                .average()
                .orElse(0.0);
    }
}
